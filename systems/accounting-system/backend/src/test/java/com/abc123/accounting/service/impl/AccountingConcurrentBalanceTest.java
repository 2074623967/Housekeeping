package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.entity.AccountEventEntity;
import com.abc123.accounting.mapper.AccountingDataMapper;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccountingConcurrentBalanceTest {

    @Autowired
    private AccountingMemoryStore accountingMemoryStore;

    @Autowired
    private AccountingEventServiceImpl eventService;

    @Autowired
    private AccountingDataMapper accountingDataMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldApplyBothCreditsWhenTransactionsOverlapOnOneAccount() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstCredited = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                accountingMemoryStore.credit("ACT10001", "CONCURRENT_CREDIT", "FIRST",
                        new BigDecimal("10.00"), "测试");
                firstCredited.countDown();
                try {
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertTrue(firstCredited.await(5, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                accountingMemoryStore.credit("ACT10001", "CONCURRENT_CREDIT", "SECOND",
                        new BigDecimal("10.00"), "测试");
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();

            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(new BigDecimal("120.00"), accountingMemoryStore.findBalance("ACT10001").getAvailableAmount());
            assertEquals(2, accountingMemoryStore.ledgers().stream()
                    .filter(item -> "CONCURRENT_CREDIT".equals(item.getBizType())).count());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldConsumeConcurrentCopiesOfOnePaymentEventOnlyOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstConsumed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try {
            Future<AccountEventDTO> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .execute(status -> {
                        AccountEventDTO event = eventService.consumePaymentSuccess(paymentRequest());
                        firstConsumed.countDown();
                        try {
                            assertTrue(releaseFirst.await(5, TimeUnit.SECONDS));
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return event;
                    }));
            assertTrue(firstConsumed.await(5, TimeUnit.SECONDS));

            Future<AccountEventDTO> second = executor.submit(() -> {
                secondStarted.countDown();
                return eventService.consumePaymentSuccess(paymentRequest());
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();

            assertEquals(first.get(5, TimeUnit.SECONDS).getEventNo(), second.get(5, TimeUnit.SECONDS).getEventNo());
            assertEquals(new BigDecimal("120.00"), accountingMemoryStore.findBalance("ACT10001").getAvailableAmount());
            assertEquals(1, eventService.list("PAYMENT_SUCCESS", "PAY-CONCURRENT-001", 1, 20).getTotal());
            assertEquals(1, accountingMemoryStore.ledgers().stream()
                    .filter(item -> "PAY-CONCURRENT-001".equals(item.getBizNo())).count());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectDuplicateEventBusinessIdentityAtDatabaseBoundary() {
        AccountEventEntity original = accountingMemoryStore.recordEvent(
                "PAYMENT_SUCCESS", "PAY-UNIQUE-001", "首次消费", "{}", "CHAIN-PAY-UNIQUE-001");
        AccountEventEntity duplicate = new AccountEventEntity();
        duplicate.setBusinessChainId(original.getBusinessChainId());
        duplicate.setEventNo("EVT-UNIQUE-SECOND");
        duplicate.setEventType(original.getEventType());
        duplicate.setBizNo(original.getBizNo());
        duplicate.setSummary(original.getSummary());
        duplicate.setPayload(original.getPayload());
        duplicate.setEventStatus(original.getEventStatus());
        duplicate.setCreatedAt(original.getCreatedAt());

        assertThrows(DataIntegrityViolationException.class, () -> accountingDataMapper.insertEvent(duplicate));
        assertEquals(1, eventService.list("PAYMENT_SUCCESS", "PAY-UNIQUE-001", 1, 20).getTotal());
    }

    private PaymentSuccessEventRequestDTO paymentRequest() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-CONCURRENT-001");
        request.setOrderNo("ORD-CONCURRENT-001");
        request.setAmount(new BigDecimal("20.00"));
        return request;
    }
}
