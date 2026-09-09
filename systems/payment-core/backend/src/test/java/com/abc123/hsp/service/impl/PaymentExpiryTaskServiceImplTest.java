package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * 支付超时关单任务服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentExpiryTaskServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldCloseExpiredPaymentAndWriteEvent() {
        ExpiredPaymentDTO payment = new ExpiredPaymentDTO();
        payment.setPaymentOrderId("PAY-EXPIRED");
        payment.setOrderNo("ORD-EXPIRED");
        payment.setChannelTransactionNo("CHANNEL-EXPIRED");
        when(paymentMapper.findExpiredPayments()).thenReturn(Collections.singletonList(payment));
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-EXPIRED")).thenReturn(1);
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentExpiryCloseResultDTO result =
                new PaymentExpiryTaskServiceImpl(paymentMapper, transactionManager).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getFailCount());
        org.junit.jupiter.api.Assertions.assertTrue(result.getFailedPaymentOrderIds().isEmpty());
        verify(transactionManager).commit(transactionStatus);
        verify(paymentMapper).markExpiredUnsubmittedPaymentClosed("PAY-EXPIRED");
        verify(paymentMapper).updatePaymentAttemptStatusByPaymentOrderId(
                "PAY-EXPIRED", "已关闭", "danger");
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-EXPIRED", "已失效", "danger");
        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_EXPIRED_CLOSED"),
                org.mockito.ArgumentMatchers.eq("PAY-EXPIRED"),
                org.mockito.ArgumentMatchers.eq("ORD-EXPIRED"),
                org.mockito.ArgumentMatchers.contains("PREPAY_EXPIRED"));
        verify(paymentMapper).insertPaymentProcessLog(org.mockito.ArgumentMatchers.argThat(log ->
                "PAYMENT_EVENT".equals(log.getSourceType())
                        && "PAY-EXPIRED".equals(log.getPaymentOrderId())
                        && "ORD-EXPIRED".equals(log.getOrderNo())
                        && log.getMessage().contains("PAYMENT_EXPIRED_CLOSED")));
    }

    @Test
    void shouldContinueClosingPaymentsAfterOneTransactionFails() {
        ExpiredPaymentDTO first = buildPayment("PAY-FIRST", "ORD-FIRST");
        ExpiredPaymentDTO failed = buildPayment("PAY-FAIL", "ORD-FAIL");
        ExpiredPaymentDTO last = buildPayment("PAY-LAST", "ORD-LAST");
        when(paymentMapper.findExpiredPayments()).thenReturn(Arrays.asList(first, failed, last));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("PAY-FAIL".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("database write failed");
            }
            return 1;
        }).when(paymentMapper).markExpiredUnsubmittedPaymentClosed(
                org.mockito.ArgumentMatchers.anyString());

        PaymentExpiryCloseResultDTO result =
                new PaymentExpiryTaskServiceImpl(paymentMapper, transactionManager).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(3, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getFailCount());
        org.junit.jupiter.api.Assertions.assertEquals(Collections.singletonList("PAY-FAIL"),
                result.getFailedPaymentOrderIds());
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager).rollback(transactionStatus);
        verify(paymentMapper).markExpiredUnsubmittedPaymentClosed("PAY-LAST");

        ArgumentCaptor<String> eventNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper, times(2)).insertEvent(
                eventNoCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_EXPIRED_CLOSED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("PREPAY_EXPIRED"));
        List<String> eventNos = eventNoCaptor.getAllValues();
        Set<String> distinctEventNos = new HashSet<String>(eventNos);
        org.junit.jupiter.api.Assertions.assertEquals(2, distinctEventNos.size());
    }

    @Test
    void shouldSkipSideEffectsWhenPaymentBecameIneligibleAfterCandidateQuery() {
        ExpiredPaymentDTO payment = buildPayment("PAY-RACING", "ORD-RACING");
        when(paymentMapper.findExpiredPayments()).thenReturn(Collections.singletonList(payment));
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-RACING")).thenReturn(0);
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentExpiryCloseResultDTO result =
                new PaymentExpiryTaskServiceImpl(paymentMapper, transactionManager).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getFailCount());
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private ExpiredPaymentDTO buildPayment(String paymentOrderId, String orderNo) {
        ExpiredPaymentDTO payment = new ExpiredPaymentDTO();
        payment.setPaymentOrderId(paymentOrderId);
        payment.setOrderNo(orderNo);
        payment.setChannelTransactionNo("CHANNEL-" + paymentOrderId);
        return payment;
    }
}
