package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.common.ClearingEventAmqpSignature;
import com.abc123.clearing.common.ClearingEventHttpSignature;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 清分事件下游派发测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingEventDispatchServiceImplTest {

    @Mock
    private ClearingMemoryStore clearingMemoryStore;

    @Mock
    private RestTemplate restTemplate;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldSignExactClearingBodyForSettlementAndAccounting() throws Exception {
        String secret = "local-clearing-http-test-secret";
        stubClearingOutbox();
        stubSuccessfulEvents();
        ClearingEventDispatchServiceImpl dispatchService = dispatchService();
        setField(dispatchService, "clearingGeneratedHttpSecret", secret);

        assertTrue(dispatchService.publishClearingGenerated("PAY-001"));

        for (String url : new String[] {"http://settlement", "http://accounting"}) {
            ArgumentCaptor<Object> request = ArgumentCaptor.forClass(Object.class);
            verify(restTemplate).postForEntity(eq(url), request.capture(), eq(Map.class));
            assertTrue(request.getValue() instanceof HttpEntity);
            HttpEntity<?> signed = (HttpEntity<?>) request.getValue();
            String body = (String) signed.getBody();
            assertEquals(ClearingEventHttpSignature.sign(body, secret),
                    signed.getHeaders().getFirst(ClearingEventHttpSignature.HEADER_NAME));
            assertEquals("CHAIN-PAY-001", new ObjectMapper().readTree(body).get("businessChainId").asText());
        }
        verify(clearingMemoryStore).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldPublishClearingGeneratedToSettlementAndAccounting() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));

        ShareItemEntity workerShare = new ShareItemEntity();
        workerShare.setShareType("WORKER");
        workerShare.setShareTargetNo("WRK-001");
        workerShare.setShareTargetName("李阿姨");
        workerShare.setShareAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001")).thenReturn(Collections.singletonList(workerShare));
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);

        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        boolean result = dispatchService.publishClearingGenerated("PAY-001");

        assertTrue(result);
        verify(restTemplate, times(1)).postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class));
        ArgumentCaptor<Object> accountingPayload = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq("http://accounting"), accountingPayload.capture(), eq(java.util.Map.class));
        assertEquals("CHAIN-PAY-001",
                new ObjectMapper().valueToTree(accountingPayload.getValue()).get("businessChainId").asText());
        assertEquals("HOME_CLEAN",
                new ObjectMapper().valueToTree(accountingPayload.getValue()).get("payScene").asText());
        ArgumentCaptor<Object> settlementPayload = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(eq("http://settlement"), settlementPayload.capture(), eq(java.util.Map.class));
        assertEquals("HOME_CLEAN",
                new ObjectMapper().valueToTree(settlementPayload.getValue()).get("payScene").asText());
        verify(clearingMemoryStore).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldUseCanonicalClearingChainForReconciliationFact() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        clearingOrder.setBusinessChainId("CHAIN-CANONICAL");
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-CANONICAL", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-CANONICAL", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class)))
                .thenReturn(reconciliationResponse(clearingOrder));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);
        setField(dispatchService, "reconciliationFactUrl", "http://reconciliation/facts");

        assertTrue(dispatchService.publishClearingGenerated("PAY-001"));

        ArgumentCaptor<Object> reconciliationPayload = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://reconciliation/facts"), reconciliationPayload.capture(), eq(java.util.Map.class));
        assertEquals("CHAIN-CANONICAL",
                new ObjectMapper().valueToTree(reconciliationPayload.getValue()).get("businessChainId").asText());
    }

    @Test
    void shouldKeepClearingOutboxRetryableWhenReconciliationFactFails() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Collections.singletonMap("code", "ERROR"), HttpStatus.BAD_GATEWAY));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);
        setField(dispatchService, "reconciliationFactUrl", "http://reconciliation/facts");

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));
        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
        verify(restTemplate).postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class));
    }

    @Test
    void shouldKeepClearingOutboxRetryableWhenAccountingReturnsBusinessFailureWithHttp200() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class)))
                .thenReturn(new ResponseEntity<>(Collections.singletonMap("code", "ACCOUNTING-400"), HttpStatus.OK));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore, "http://settlement", "http://accounting", "ACT10002", restTemplate);

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));
        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldNotMarkClearingPublishedWhenAccountingUrlIsMissing() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore, "http://settlement", " ", "ACT10002", restTemplate);

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));
        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
        verify(restTemplate, times(1)).postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class));
    }

    @Test
    void shouldRejectSettlementReceiptForAnotherClearingOrder() {
        stubClearingOutbox();
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-OTHER"));

        assertFalse(dispatchService().publishClearingGenerated("PAY-001"));

        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(restTemplate, times(0)).postForEntity(eq("http://accounting"), any(), eq(Map.class));
    }

    @Test
    void shouldRejectAccountingReceiptForAnotherBusinessChain() {
        stubClearingOutbox();
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(Map.class)))
                .thenReturn(eventResponse("CHAIN-OTHER", "CLR-001"));

        assertFalse(dispatchService().publishClearingGenerated("PAY-001"));

        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldRejectReconciliationReceiptForAnotherBusinessChain() {
        ClearingOrderEntity clearingOrder = stubClearingOutbox();
        stubSuccessfulEvents();
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(reconciliationResponse(clearingOrder, "CHAIN-OTHER", clearingOrder.getOrderAmount()));
        ClearingEventDispatchServiceImpl dispatchService = dispatchService();
        setField(dispatchService, "reconciliationFactUrl", "http://reconciliation/facts");

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));

        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldRejectReconciliationReceiptWithWrongClearingAmount() {
        ClearingOrderEntity clearingOrder = stubClearingOutbox();
        stubSuccessfulEvents();
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(reconciliationResponse(clearingOrder, "CHAIN-PAY-001", new BigDecimal("169.00")));
        ClearingEventDispatchServiceImpl dispatchService = dispatchService();
        setField(dispatchService, "reconciliationFactUrl", "http://reconciliation/facts");

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));

        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldPublishClearingGeneratedToAmqpWithPublisherConfirm() {
        ClearingOrderEntity clearingOrder = new ClearingOrderEntity();
        clearingOrder.setClearingNo("CLR-001");
        clearingOrder.setPaymentOrderId("PAY-001");
        clearingOrder.setWorkerAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001")).thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                rabbitTemplate,
                new ObjectMapper(),
                "clearing.trade",
                "clearing.generated.v1");
        setField(dispatchService, "clearingGeneratedAmqpSecret", "local-clearing-amqp-test-secret");
        boolean result = dispatchService.publishClearingGenerated("PAY-001");

        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("clearing.trade"), eq("clearing.generated.v1"),
                eq(outboxEvent().getPayload()), processorCaptor.capture());
        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        Message message = processorCaptor.getValue().postProcessMessage(new Message(
                outboxEvent().getPayload().getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        assertTrue("EVT-001".equals(message.getMessageProperties().getMessageId()));
        assertTrue("CLR-001".equals(message.getMessageProperties().getHeaders().get("clearingNo")));
        assertTrue("1".equals(message.getMessageProperties().getHeaders().get("schemaVersion")));
        assertEquals(ClearingEventAmqpSignature.sign(message.getBody(), "local-clearing-amqp-test-secret"),
                message.getMessageProperties().getHeaders().get(ClearingEventAmqpSignature.HEADER_NAME));
        verify(clearingMemoryStore).markOutboxPublishSuccess("EVT-001");
    }

    @Test
    void shouldKeepClearingOutboxRetryableWhenAmqpSecretIsMissing() {
        ClearingOrderEntity clearingOrder = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore, rabbitTemplate, new ObjectMapper(), "clearing.trade", "clearing.generated.v1");

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));
        verify(rabbitTemplate, times(0)).invoke(any());
        verify(clearingMemoryStore).markOutboxPublishFailed("EVT-001");
    }

    @Test
    void shouldSkipDownstreamDispatchWhenOutboxAlreadyPublished() {
        ClearingOrderEntity clearingOrder = new ClearingOrderEntity();
        clearingOrder.setClearingNo("CLR-001");
        clearingOrder.setPaymentOrderId("PAY-001");
        clearingOrder.setWorkerAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));

        ClearingEventEntity publishedEvent = outboxEvent();
        publishedEvent.setPublishStatus("SUCCESS");
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(publishedEvent);

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        boolean result = dispatchService.publishClearingGenerated("PAY-001");

        assertTrue(result);
        verify(restTemplate, times(0)).postForEntity(eq("http://settlement"), any(), eq(java.util.Map.class));
        verify(restTemplate, times(0)).postForEntity(eq("http://accounting"), any(), eq(java.util.Map.class));
        verify(clearingMemoryStore, times(0)).markOutboxPublishSuccess("EVT-001");
        verify(clearingMemoryStore, times(0)).markOutboxPublishFailed("EVT-001");
    }

    @Test
    void shouldSkipDispatchWhenAnotherConsumerClaimsOutboxFirst() {
        ClearingOrderEntity clearingOrder = new ClearingOrderEntity();
        clearingOrder.setClearingNo("CLR-001");
        clearingOrder.setPaymentOrderId("PAY-001");
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(false);

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore, "http://settlement", "http://accounting", "ACT10002", restTemplate);

        assertFalse(dispatchService.publishClearingGenerated("PAY-001"));
        verify(restTemplate, times(0)).postForEntity(any(), any(), eq(java.util.Map.class));
        verify(clearingMemoryStore, times(0)).markOutboxPublishFailed("EVT-001");
    }

    @Test
    void shouldReturnFalseWhenPaymentOrderMissing() {
        when(clearingMemoryStore.orders()).thenReturn(Collections.<ClearingOrderEntity>emptyList());

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        assertFalse(dispatchService.publishClearingGenerated("PAY-NOT-FOUND"));
    }

    private ClearingEventEntity outboxEvent() {
        ClearingEventEntity event = new ClearingEventEntity();
        event.setEventNo("EVT-001");
        event.setEventType("CLEARING_GENERATED");
        event.setBizNo("CLR-001");
        event.setPayload("{\"clearingNo\":\"CLR-001\",\"amount\":120.00}");
        return event;
    }

    private ClearingOrderEntity clearingOrder() {
        ClearingOrderEntity order = new ClearingOrderEntity();
        order.setClearingNo("CLR-001");
        order.setPaymentOrderId("PAY-001");
        order.setBusinessChainId("CHAIN-PAY-001");
        order.setPayScene("HOME_CLEAN");
        order.setOrderAmount(new BigDecimal("168.00"));
        order.setWorkerAmount(new BigDecimal("120.00"));
        return order;
    }

    private ClearingOrderEntity stubClearingOutbox() {
        ClearingOrderEntity order = clearingOrder();
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(order));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001"))
                .thenReturn(Collections.<ShareItemEntity>emptyList());
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());
        when(clearingMemoryStore.tryClaimOutboxPublish("EVT-001")).thenReturn(true);
        return order;
    }

    private void stubSuccessfulEvents() {
        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(Map.class)))
                .thenReturn(eventResponse("CHAIN-PAY-001", "CLR-001"));
    }

    private ClearingEventDispatchServiceImpl dispatchService() {
        return new ClearingEventDispatchServiceImpl(
                clearingMemoryStore, "http://settlement", "http://accounting", "ACT10002", restTemplate);
    }

    private ResponseEntity<Map> eventResponse(String businessChainId, String clearingNo) {
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("businessChainId", businessChainId);
        event.put("eventNo", "ACK-001");
        event.put("eventType", "CLEARING_GENERATED");
        event.put("bizNo", clearingNo);
        event.put("eventStatus", "已消费");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "0");
        body.put("data", event);
        return new ResponseEntity<Map>(body, HttpStatus.OK);
    }

    private ResponseEntity<Map> reconciliationResponse(ClearingOrderEntity order) {
        return reconciliationResponse(order, order.getBusinessChainId(), order.getOrderAmount());
    }

    private ResponseEntity<Map> reconciliationResponse(ClearingOrderEntity order,
                                                       String businessChainId, BigDecimal amount) {
        Map<String, Object> fact = new LinkedHashMap<String, Object>();
        fact.put("businessChainId", businessChainId);
        fact.put("systemCode", "clearing-system");
        fact.put("factType", "CLEARING_GENERATED");
        fact.put("factNo", order.getClearingNo());
        fact.put("businessNo", order.getPaymentOrderId());
        fact.put("payScene", order.getPayScene());
        fact.put("factStatus", "SUCCESS");
        fact.put("amount", amount);
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("businessChainId", businessChainId);
        snapshot.put("facts", Collections.singletonList(fact));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "0");
        body.put("data", snapshot);
        return new ResponseEntity<Map>(body, HttpStatus.OK);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法设置测试字段 " + fieldName, exception);
        }
    }
}
