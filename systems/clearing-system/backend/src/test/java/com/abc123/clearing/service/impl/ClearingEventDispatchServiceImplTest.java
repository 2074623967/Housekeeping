package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
    void shouldPublishClearingGeneratedToSettlementAndAccounting() {
        ClearingOrderEntity clearingOrder = new ClearingOrderEntity();
        clearingOrder.setClearingNo("CLR-001");
        clearingOrder.setPaymentOrderId("PAY-001");
        clearingOrder.setWorkerAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));

        ShareItemEntity workerShare = new ShareItemEntity();
        workerShare.setShareType("WORKER");
        workerShare.setShareTargetNo("WRK-001");
        workerShare.setShareTargetName("李阿姨");
        workerShare.setShareAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001")).thenReturn(Collections.singletonList(workerShare));
        when(clearingMemoryStore.findClearingGeneratedOutboxEvent("PAY-001")).thenReturn(outboxEvent());

        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        boolean result = dispatchService.publishClearingGenerated("PAY-001");

        assertTrue(result);
        verify(restTemplate, times(1)).postForEntity(eq("http://settlement"), any(), eq(String.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://accounting"), any(), eq(String.class));
        verify(clearingMemoryStore).markOutboxPublishSuccess("EVT-001");
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

        boolean result = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                rabbitTemplate,
                new ObjectMapper(),
                "clearing.trade",
                "clearing.generated.v1").publishClearingGenerated("PAY-001");

        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("clearing.trade"), eq("clearing.generated.v1"),
                eq(outboxEvent().getPayload()), processorCaptor.capture());
        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        Message message = processorCaptor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertTrue("EVT-001".equals(message.getMessageProperties().getMessageId()));
        assertTrue("CLR-001".equals(message.getMessageProperties().getHeaders().get("clearingNo")));
        verify(clearingMemoryStore).markOutboxPublishSuccess("EVT-001");
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
}
