package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
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
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO result = eventService.consumePaymentSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10001");

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals("{\"orderNo\":\"ORD202607200099\",\"payScene\":\"HOME_CLEAN\",\"amount\":20.00}", result.getPayload());
        assertEquals("¥120.00", balance.getAvailableAmount());
        assertEquals("HOME_CLEAN", accountingMemoryStore.ledgers().stream()
                .filter(item -> "PAY202607200099".equals(item.getBizNo()))
                .findFirst().map(item -> item.getPayScene()).orElse(null));
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
    void shouldRejectPaymentSuccessReplayWithDifferentBusinessOrder() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-ORDER-DRIFT");
        request.setOrderNo("ORD-ORIGINAL");
        request.setAmount(new BigDecimal("20.00"));

        eventService.consumePaymentSuccess(request);
        request.setOrderNo("ORD-OTHER");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
        assertEquals("¥120.00", balanceService.detail("ACT10001").getAvailableAmount());
        assertEquals(1, eventService.list("PAYMENT_SUCCESS", "PAY-ORDER-DRIFT", 1, 20).getTotal());
    }

    @Test
    void shouldKeepPaymentSuccessPayloadValidForQuotedOrderNumber() throws Exception {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-QUOTED-ORDER");
        request.setOrderNo("ORD-\"QUOTED");
        request.setPayScene("HOME_\"CLEAN");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO first = eventService.consumePaymentSuccess(request);
        AccountEventDTO replay = eventService.consumePaymentSuccess(request);

        assertEquals(first.getEventNo(), replay.getEventNo());
        com.fasterxml.jackson.databind.JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first.getPayload());
        assertEquals(request.getOrderNo(), payload.get("orderNo").asText());
        assertEquals(request.getPayScene(), payload.get("payScene").asText());
        assertEquals("¥120.00", balanceService.detail("ACT10001").getAvailableAmount());
    }

    @Test
    void shouldRejectLegacyPaymentSuccessReplayWithoutStoredOrderNumber() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-LEGACY-NO-ORDER");
        request.setOrderNo("ORD-UNVERIFIED");
        request.setAmount(new BigDecimal("20.00"));
        accountingMemoryStore.recordEvent("PAYMENT_SUCCESS", request.getPaymentOrderId(),
                "历史支付事件", "{\"amount\":20.00}");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
        assertEquals("¥100.00", balanceService.detail("ACT10001").getAvailableAmount());
    }

    @Test
    void shouldRejectPaymentSuccessReplayForDifferentAccount() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-ACCOUNT-DRIFT");
        request.setOrderNo("ORD-ACCOUNT-DRIFT");
        request.setAmount(new BigDecimal("20.00"));

        eventService.consumePaymentSuccess(request);
        request.setAccountNo("ACT10002");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
        assertEquals("¥120.00", balanceService.detail("ACT10001").getAvailableAmount());
        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());
    }

    @Test
    void shouldRejectRepeatedPaymentSuccessWithChangedAmount() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202607200199");
        request.setOrderNo("ORD202607200199");
        request.setAmount(new BigDecimal("20.00"));

        eventService.consumePaymentSuccess(request);
        request.setAmount(new BigDecimal("21.00"));

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
    }

    @Test
    void shouldRejectRepeatedPaymentSuccessWithChangedPayScene() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202607200201");
        request.setOrderNo("ORD202607200201");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("20.00"));

        eventService.consumePaymentSuccess(request);
        request.setPayScene("HOME_REPAIR");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
    }

    @Test
    void shouldRejectPaymentSuccessWithMismatchedBusinessChain() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY202607200299");
        request.setBusinessChainId("CHAIN-OTHER");
        request.setOrderNo("ORD202607200299");
        request.setAmount(new BigDecimal("20.00"));

        assertThrows(IllegalArgumentException.class, () -> eventService.consumePaymentSuccess(request));
    }

    @Test
    void shouldDebitBalanceForRefundSuccessAndKeepConsumptionIdempotent() {
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        request.setBusinessChainId("CHAIN-PAY202607200099");
        request.setAccountNo("ACT10003");
        request.setRefundOrderId("REF202607200099");
        request.setPaymentOrderId("PAY202607200099");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("5.00"));

        AccountEventDTO first = eventService.consumeRefundSuccess(request);
        AccountEventDTO second = eventService.consumeRefundSuccess(request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10003");
        PageResultDTO<AccountEventDTO> events =
                eventService.list("REFUND_SUCCESS", "REF202607200099", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals("REFUND_SUCCESS", first.getEventType());
        assertEquals("CHAIN-PAY202607200099", first.getBusinessChainId());
        assertEquals("{\"paymentOrderId\":\"PAY202607200099\",\"payScene\":\"HOME_CLEAN\",\"amount\":5.00}", first.getPayload());
        assertEquals("HOME_CLEAN", accountingMemoryStore.ledgers().stream()
                .filter(item -> "REF202607200099".equals(item.getBizNo()))
                .findFirst().map(item -> item.getPayScene()).orElse(null));
        assertEquals("¥7.00", balance.getAvailableAmount());
        assertEquals(1, events.getTotal());
        assertEquals(true, accountingMemoryStore.ledgers().stream()
                .filter(item -> "REF202607200099".equals(item.getBizNo()))
                .allMatch(item -> item.getCreatedAt().compareTo(first.getCreatedAt()) >= 0));
    }

    @Test
    void shouldRejectRefundSuccessReplayForDifferentAccount() {
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        request.setAccountNo("ACT10003");
        request.setRefundOrderId("REF-ACCOUNT-DRIFT");
        request.setPaymentOrderId("PAY-ACCOUNT-DRIFT");
        request.setAmount(new BigDecimal("5.00"));

        eventService.consumeRefundSuccess(request);
        request.setAccountNo("ACT10002");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumeRefundSuccess(request));
        assertEquals("¥7.00", balanceService.detail("ACT10003").getAvailableAmount());
        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());
    }

    @Test
    void shouldRejectRefundSuccessWithMismatchedBusinessChain() {
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        request.setBusinessChainId("CHAIN-OTHER");
        request.setAccountNo("ACT10003");
        request.setRefundOrderId("REF202607200299");
        request.setPaymentOrderId("PAY202607200299");
        request.setAmount(new BigDecimal("5.00"));

        assertThrows(IllegalArgumentException.class, () -> eventService.consumeRefundSuccess(request));
    }

    @Test
    void shouldRejectClearingGeneratedWithMismatchedPaymentBusinessChain() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setBusinessChainId("CHAIN-OTHER");
        request.setAccountNo("ACT10002");
        request.setClearingOrderNo("CLR202607200299");
        request.setBizNo("PAY202607200299");
        request.setAmount(new BigDecimal("20.00"));

        assertThrows(IllegalArgumentException.class, () -> eventService.consumeClearingGenerated(request));
    }

    @Test
    void shouldPreservePaySceneWhenClearingGeneratedEventConsumed() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setClearingOrderNo("CLR202607200301");
        request.setBizNo("PAY202607200301");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO result = eventService.consumeClearingGenerated(request);

        assertEquals("{\"bizNo\":\"PAY202607200301\",\"payScene\":\"HOME_CLEAN\",\"amount\":20.00}",
                result.getPayload());
        assertEquals("HOME_CLEAN", accountingMemoryStore.ledgers().stream()
                .filter(item -> "CLR202607200301".equals(item.getBizNo()))
                .findFirst().map(item -> item.getPayScene()).orElse(null));
    }

    @Test
    void shouldRejectClearingGeneratedReplayForDifferentAccount() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setClearingOrderNo("CLR-ACCOUNT-DRIFT");
        request.setBizNo("PAY-ACCOUNT-DRIFT");
        request.setAmount(new BigDecimal("20.00"));

        eventService.consumeClearingGenerated(request);
        request.setAccountNo("ACT10001");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumeClearingGenerated(request));
        assertEquals("¥140.00", balanceService.detail("ACT10002").getAvailableAmount());
        assertEquals("¥100.00", balanceService.detail("ACT10001").getAvailableAmount());
    }

    @Test
    void shouldDeriveClearingBusinessChainFromPaymentOrder() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setClearingOrderNo("CLR202607200300");
        request.setBizNo("PAY202607200300");
        request.setAmount(new BigDecimal("20.00"));

        AccountEventDTO result = eventService.consumeClearingGenerated(request);

        assertEquals("CHAIN-PAY202607200300", result.getBusinessChainId());
        assertEquals("消费清分生成事件", result.getSummary());
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
    void shouldRejectSettlementCompensationReplayForDifferentAccount() {
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setSettlementOrderNo("SETTLE-ACCOUNT-DRIFT");
        request.setBizNo("SETTLE-DIFF-ACCOUNT-DRIFT");
        request.setAmount(new BigDecimal("5.00"));
        request.setCompensationDirection("贷方");

        eventService.consumeSettlementCompensation(request);
        request.setAccountNo("ACT10003");

        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));
        assertEquals("¥125.00", balanceService.detail("ACT10002").getAvailableAmount());
        assertEquals("¥12.00", balanceService.detail("ACT10003").getAvailableAmount());
    }

    @Test
    void shouldRejectSettlementCompensationReplayWithChangedBusinessFacts() {
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setSettlementOrderNo("SETTLE-FACT-DRIFT");
        request.setBizNo("SETTLE-DIFF-FACT-DRIFT");
        request.setAmount(new BigDecimal("5.00"));
        request.setCompensationDirection("贷方");

        AccountEventDTO original = eventService.consumeSettlementCompensation(request);
        request.setAmount(new BigDecimal("5.0"));
        assertEquals(original.getEventNo(), eventService.consumeSettlementCompensation(request).getEventNo());

        request.setAmount(new BigDecimal("6.00"));
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));
        request.setAmount(new BigDecimal("5.00"));
        request.setCompensationDirection("借方");
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));
        request.setCompensationDirection("贷方");
        request.setBizNo("SETTLE-DIFF-OTHER");
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));

        assertEquals("¥125.00", balanceService.detail("ACT10002").getAvailableAmount());
        assertEquals(1, eventService.list("SETTLEMENT_COMPENSATION", "SETTLE-FACT-DRIFT", 1, 20).getTotal());
    }

    @Test
    void shouldRejectInvalidSettlementCompensationBeforeRecording() {
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setSettlementOrderNo("SETTLE-INVALID");
        request.setBizNo("SETTLE-DIFF-INVALID");
        request.setAmount(new BigDecimal("5.00"));
        request.setCompensationDirection("未知方向");
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));

        request.setCompensationDirection("贷方");
        request.setAmount(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));
        request.setAmount(new BigDecimal("-5.00"));
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));

        request.setAmount(new BigDecimal("5.00"));
        request.setBizNo(" ");
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));
        request.setBizNo("SETTLE-DIFF-INVALID");
        request.setSettlementOrderNo(" ");
        assertThrows(IllegalArgumentException.class, () -> eventService.consumeSettlementCompensation(request));

        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());
        assertEquals(0, eventService.list("SETTLEMENT_COMPENSATION", null, 1, 20).getTotal());
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
