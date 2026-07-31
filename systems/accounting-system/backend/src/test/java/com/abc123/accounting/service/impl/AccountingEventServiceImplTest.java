package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 账务事件服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccountingEventServiceImplTest {

    @Autowired
    private AccountingEventServiceImpl eventService;

    @Autowired
    private BalanceServiceImpl balanceService;

    @Test
    void shouldCreditBalanceWhenPaymentSuccessEventConsumed() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202607200099");
        request.setOrderNo("ORD202607200099");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO result = eventService.consumePaymentSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10001");

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals("¥120.00", balance.getAvailableAmount());
    }

    @Test
    void shouldKeepPaymentSuccessConsumptionIdempotent() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202607200188");
        request.setOrderNo("ORD202607200188");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO first = eventService.consumePaymentSuccess(request);
        AccountEventDTO second = eventService.consumePaymentSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10001");
        PageResultDTO<AccountEventDTO> events = eventService.list("PAYMENT_SUCCESS", "PAY202607200188", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals("¥120.00", balance.getAvailableAmount());
        assertEquals(1, events.getTotal());
    }
}
