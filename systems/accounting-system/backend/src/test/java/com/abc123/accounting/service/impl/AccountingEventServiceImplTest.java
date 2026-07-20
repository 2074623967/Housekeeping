package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 账务事件服务测试。
 */
class AccountingEventServiceImplTest {

    @Test
    void shouldCreditBalanceWhenPaymentSuccessEventConsumed() {
        AccountingMemoryStore store = new AccountingMemoryStore();
        store.initDemoData();
        AccountingMapper mapper = new AccountingMapper();
        AccountingEventServiceImpl eventService = new AccountingEventServiceImpl(store, mapper);
        BalanceServiceImpl balanceService = new BalanceServiceImpl(store, mapper);

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
}
