package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.dto.SettlementCompensationEventRequestDTO;
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

    @Autowired
    private AccountingMemoryStore accountingMemoryStore;

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

    @Test
    void shouldDebitBalanceForRefundSuccessAndKeepConsumptionIdempotent() {
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        request.setBusinessChainId("CHAIN-PAY202607200099");
        request.setAccountNo("ACT10003");
        request.setRefundOrderId("REF202607200099");
        request.setPaymentOrderId("PAY202607200099");
        request.setAmount(new BigDecimal("5.00"));

        AccountEventDTO first = eventService.consumeRefundSuccess(request);
        AccountEventDTO second = eventService.consumeRefundSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10003");
        PageResultDTO<AccountEventDTO> events =
                eventService.list("REFUND_SUCCESS", "REF202607200099", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals("REFUND_SUCCESS", first.getEventType());
        assertEquals("CHAIN-PAY202607200099", first.getBusinessChainId());
        assertEquals("¥7.00", balance.getAvailableAmount());
        assertEquals(1, events.getTotal());
    }

    @Test
    void shouldNotCreditAgainWhenHistoricalEventAlreadyExistsWithoutLedger() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202608020001");
        request.setOrderNo("ORD202608020001");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("20.00"));

        accountingMemoryStore.recordEvent(
                "PAYMENT_SUCCESS",
                "PAY202608020001",
                "历史演练已消费事件",
                "{\"orderNo\":\"ORD202608020001\",\"amount\":20.00}");

        AccountEventDTO result = eventService.consumePaymentSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10001");
        PageResultDTO<AccountEventDTO> events = eventService.list("PAYMENT_SUCCESS", "PAY202608020001", 1, 20);

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals("¥100.00", balance.getAvailableAmount());
        assertEquals(1, events.getTotal());
    }

    @Test
    void shouldConsumeSettlementCompensationAsCreditAndKeepIdempotent() {
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setSettlementOrderNo("SETTLE-COMP-20260807-001");
        request.setBizNo("SETTLE-DIFF-20260807-001");
        request.setAmount(new BigDecimal("15.00"));
        request.setCompensationDirection("贷方");
        request.setSummary("服务者补差入账");
        request.setOperatorName("财务补偿专员");

        AccountEventDTO first = eventService.consumeSettlementCompensation(request);
        AccountEventDTO second = eventService.consumeSettlementCompensation(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10002");
        PageResultDTO<AccountEventDTO> events =
                eventService.list("SETTLEMENT_COMPENSATION", "SETTLE-COMP-20260807-001", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals("¥135.00", balance.getAvailableAmount());
        assertEquals(1, events.getTotal());
    }

    @Test
    void shouldConsumeSettlementCompensationAsDebit() {
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        request.setAccountNo("ACT10003");
        request.setSettlementOrderNo("SETTLE-COMP-20260807-002");
        request.setBizNo("SETTLE-DIFF-20260807-002");
        request.setAmount(new BigDecimal("5.00"));
        request.setCompensationDirection("借方");
        request.setSummary("平台手续费冲回");
        request.setOperatorName("财务补偿专员");

        AccountEventDTO result = eventService.consumeSettlementCompensation(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10003");

        assertEquals("SETTLEMENT_COMPENSATION", result.getEventType());
        assertEquals("¥7.00", balance.getAvailableAmount());
    }
}
