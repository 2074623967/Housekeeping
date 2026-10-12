package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.ClearingReplayResultDTO;
import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.mapper.ClearingDataMapper;
import com.abc123.clearing.service.ShareService;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

/**
 * 清分事件服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:clearing_event_service_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE")
class ClearingEventServiceImplTest {

    @Autowired
    private ClearingEventServiceImpl clearingEventService;

    @Autowired
    private ShareService shareService;

    @Autowired
    private ClearingOrderServiceImpl clearingOrderService;

    @Autowired
    private ClearingDataMapper clearingDataMapper;

    @Test
    void shouldGenerateClearingArtifactsWhenPaymentSuccessConsumed() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200099");
        request.setOrderNo("ORD202607200099");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("王先生");
        request.setMerchantName("浦东门店");
        request.setWorkerName("赵阿姨");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("200.00"));

        ClearingEventDTO result = clearingEventService.consumePaymentSuccess(request);
        PageResultDTO<ShareItemDTO> shares = shareService.list("", "", 1, 20);
        PageResultDTO<ClearingOrderDTO> orders = clearingOrderService.list("", "", "PAY202607200099", "", 1, 20);

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals(6, shares.getTotal());
        assertEquals(1, orders.getTotal());
        assertEquals("PAY202607200099", orders.getItems().get(0).getPaymentOrderId());
        assertEquals("HOME_CLEAN", orders.getItems().get(0).getPayScene());
        assertEquals("{\"paymentOrderId\":\"PAY202607200099\",\"orderNo\":\"ORD202607200099\","
                + "\"businessChainId\":\"CHAIN-PAY202607200099\",\"payScene\":\"HOME_CLEAN\"}", result.getPayload());
    }

    @Test
    void shouldKeepConsumptionIdempotentWhenPaymentSuccessRepeated() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200188");
        request.setOrderNo("ORD202607200188");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("李女士");
        request.setMerchantName("徐汇门店");
        request.setWorkerName("陈阿姨");
        request.setAmount(new BigDecimal("300.00"));

        ClearingEventDTO first = clearingEventService.consumePaymentSuccess(request);
        request.setAmount(new BigDecimal("300.0"));
        ClearingEventDTO second = clearingEventService.consumePaymentSuccess(request);
        PageResultDTO<ShareItemDTO> shares = shareService.list("", "", 1, 20);
        PageResultDTO<ClearingOrderDTO> orders = clearingOrderService.list("", "", "PAY202607200188", "", 1, 20);
        PageResultDTO<ClearingEventDTO> events = clearingEventService.list("PAYMENT_SUCCESS", "PAY202607200188", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals(1, orders.getTotal());
        assertEquals(1, events.getTotal());
        assertEquals(6, shares.getTotal());
    }

    @Test
    void shouldEnforceOneClearingEventPerTypeAndBusinessNumber() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200189");
        request.setOrderNo("ORD202607200189");
        request.setAmount(new BigDecimal("300.00"));
        clearingEventService.consumePaymentSuccess(request);

        ClearingEventEntity original = clearingDataMapper.findEventByTypeAndBizNo(
                "PAYMENT_SUCCESS", request.getPaymentOrderId());
        ClearingEventEntity duplicate = new ClearingEventEntity();
        duplicate.setBusinessChainId(original.getBusinessChainId());
        duplicate.setEventNo("EVT-DUPLICATE-189");
        duplicate.setEventType(original.getEventType());
        duplicate.setBizNo(original.getBizNo());
        duplicate.setSummary(original.getSummary());
        duplicate.setPayload(original.getPayload());
        duplicate.setEventStatus(original.getEventStatus());
        duplicate.setPublishStatus(original.getPublishStatus());
        duplicate.setRetryCount(0);
        duplicate.setCreatedAt(original.getCreatedAt());

        assertThrows(DataIntegrityViolationException.class, () -> clearingDataMapper.insertEvent(duplicate));
        assertEquals(1, clearingEventService.list(
                "PAYMENT_SUCCESS", request.getPaymentOrderId(), 1, 20).getTotal());

        String clearingNo = clearingOrderService.list("", "", request.getPaymentOrderId(), "", 1, 20)
                .getItems().get(0).getClearingNo();
        ClearingEventEntity generated = clearingDataMapper.findEventByTypeAndBizNo("CLEARING_GENERATED", clearingNo);
        duplicate.setBusinessChainId(generated.getBusinessChainId());
        duplicate.setEventNo("EVT-DUPLICATE-GEN");
        duplicate.setEventType(generated.getEventType());
        duplicate.setBizNo(generated.getBizNo());
        duplicate.setSummary(generated.getSummary());
        duplicate.setPayload(generated.getPayload());
        duplicate.setEventStatus(generated.getEventStatus());
        duplicate.setPublishStatus(generated.getPublishStatus());
        duplicate.setCreatedAt(generated.getCreatedAt());

        assertThrows(DataIntegrityViolationException.class, () -> clearingDataMapper.insertEvent(duplicate));
        assertEquals(1, clearingEventService.list("CLEARING_GENERATED", clearingNo, 1, 20).getTotal());
    }

    @Test
    void shouldRejectRepeatedPaymentSuccessWithMismatchedPayScene() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200199");
        request.setOrderNo("ORD202607200199");
        request.setBatchDate("2026-07-20");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("300.00"));

        clearingEventService.consumePaymentSuccess(request);
        request.setPayScene("HOME_REPAIR");

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
        assertEquals(1, clearingOrderService.list("", "", "PAY202607200199", "", 1, 20).getTotal());
    }

    @Test
    void shouldRejectRepeatedPaymentSuccessWithMismatchedOrderNo() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200200");
        request.setOrderNo("ORD202607200200");
        request.setAmount(new BigDecimal("300.00"));

        ClearingEventDTO original = clearingEventService.consumePaymentSuccess(request);
        request.setOrderNo("ORD-OTHER");

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
        assertEquals(1, clearingOrderService.list("", "", request.getPaymentOrderId(), "", 1, 20).getTotal());
        assertEquals(original.getEventNo(), clearingEventService.list(
                "PAYMENT_SUCCESS", request.getPaymentOrderId(), 1, 20).getItems().get(0).getEventNo());
    }

    @Test
    void shouldRejectRepeatedPaymentSuccessWithMismatchedAmount() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200201");
        request.setOrderNo("ORD202607200201");
        request.setAmount(new BigDecimal("300.00"));

        ClearingEventDTO original = clearingEventService.consumePaymentSuccess(request);
        request.setAmount(new BigDecimal("301.00"));

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
        assertEquals("¥300.00", clearingOrderService.list(
                "", "", request.getPaymentOrderId(), "", 1, 20).getItems().get(0).getOrderAmount());
        assertEquals(original.getEventNo(), clearingEventService.list(
                "PAYMENT_SUCCESS", request.getPaymentOrderId(), 1, 20).getItems().get(0).getEventNo());
    }

    @Test
    void shouldRejectPaymentSuccessWithMismatchedBusinessChain() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200299");
        request.setBusinessChainId("CHAIN-OTHER");
        request.setOrderNo("ORD202607200299");
        request.setBatchDate("2026-07-20");
        request.setAmount(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
    }

    @Test
    void shouldRejectPaymentSuccessWithMissingIdentifiersBeforeCreatingClearingArtifacts() {
        assertPaymentSuccessIdentifierRejected(null, "ORD202607200310");
        assertPaymentSuccessIdentifierRejected(" ", "ORD202607200310");
        assertPaymentSuccessIdentifierRejected("PAY202607200310", null);
        assertPaymentSuccessIdentifierRejected("PAY202607200310", "  ");
    }

    @Test
    void shouldRejectPaymentSuccessWithOversizedOrPaddedIdentifiers() {
        assertPaymentSuccessIdentifierRejected(String.join("", Collections.nCopies(65, "P")), "ORD202607200311");
        assertPaymentSuccessIdentifierRejected("PAY202607200311", String.join("", Collections.nCopies(65, "O")));
        assertPaymentSuccessIdentifierRejected(" PAY202607200311", "ORD202607200311");
    }

    private void assertPaymentSuccessIdentifierRejected(String paymentOrderId, String orderNo) {
        int originalOrders = clearingOrderService.list("", "", "", "", 1, 20).getTotal();
        int originalEvents = clearingEventService.list("PAYMENT_SUCCESS", "", 1, 20).getTotal();
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId(paymentOrderId);
        request.setOrderNo(orderNo);
        request.setAmount(new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
        assertEquals(originalOrders, clearingOrderService.list("", "", "", "", 1, 20).getTotal());
        assertEquals(originalEvents, clearingEventService.list("PAYMENT_SUCCESS", "", 1, 20).getTotal());
    }

    @Test
    void shouldRejectPaymentSuccessWithNonPositiveAmount() {
        assertPaymentSuccessAmountRejected(BigDecimal.ZERO, "PAY202607200300");
        assertPaymentSuccessAmountRejected(new BigDecimal("-1.00"), "PAY202607200301");
    }

    @Test
    void shouldRejectPaymentSuccessWithMoreThanTwoDecimalPlaces() {
        assertPaymentSuccessAmountRejected(new BigDecimal("100.001"), "PAY202607200302");
    }

    @Test
    void shouldRejectPaymentSuccessAboveDecimalAmountRange() {
        assertPaymentSuccessAmountRejected(new BigDecimal("10000000000000000.00"), "PAY202607200303");
    }

    private void assertPaymentSuccessAmountRejected(BigDecimal amount, String paymentOrderId) {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId(paymentOrderId);
        request.setOrderNo("ORD-" + paymentOrderId);
        request.setAmount(amount);

        assertThrows(IllegalArgumentException.class,
                () -> clearingEventService.consumePaymentSuccess(request));
        assertEquals(0, clearingOrderService.list("", "", paymentOrderId, "", 1, 20).getTotal());
    }

    @Test
    void shouldReplayDueFailedEventsThroughExistingRetryService() {
        ClearingReplayResultDTO result = clearingEventService.replayDueFailedEvents();

        assertEquals(0, result.getAttemptedCount());
        assertEquals("当前没有到达重试时间的失败派发事件", result.getMessage());
    }
}
