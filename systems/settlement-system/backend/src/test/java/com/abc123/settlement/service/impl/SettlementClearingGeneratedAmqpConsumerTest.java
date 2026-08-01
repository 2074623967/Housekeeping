package com.abc123.settlement.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.service.SettlementEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** 清分结果结算 AMQP 消费适配测试。 */
@ExtendWith(MockitoExtension.class)
class SettlementClearingGeneratedAmqpConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SettlementEventService settlementEventService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    @Test
    void shouldConsumeAndAckWhenSettlementCreationSucceeds() throws Exception {
        consumer(3).consume(message(0), channel, 31L);

        verify(settlementEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));
        verify(channel).basicAck(31L, false);
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
    }

    @Test
    void shouldRouteToRetryWhenSettlementCreationFails() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(settlementEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));

        consumer(3).consume(message(0), channel, 32L);

        ArgumentCaptor<Message> retryMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("clearing.trade.retry"), eq("clearing.generated.settlement.retry.v1"), retryMessage.capture());
        verify(channel).basicAck(32L, false);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                retryMessage.getValue().getMessageProperties().getHeaders().get("x-retry-count"));
    }

    @Test
    void shouldRouteToDeadLetterWhenSettlementRetryLimitReached() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("permanent failure"))
                .when(settlementEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));

        consumer(3).consume(message(3), channel, 33L);

        verify(rabbitTemplate).send(eq("clearing.trade.dlq"), eq("clearing.generated.settlement.dlq.v1"), any(Message.class));
        verify(channel).basicAck(33L, false);
    }

    private SettlementClearingGeneratedAmqpConsumer consumer(int maxRetryCount) {
        return new SettlementClearingGeneratedAmqpConsumer(
                settlementEventService,
                rabbitTemplate,
                objectMapper,
                "clearing.trade.retry",
                "clearing.generated.settlement.retry.v1",
                "clearing.trade.dlq",
                "clearing.generated.settlement.dlq.v1",
                maxRetryCount);
    }

    private Message message(int retryCount) throws Exception {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLR-001");
        request.setPaymentOrderId("PAY-001");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-001");
        request.setTargetName("张阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("EVT-001");
        properties.setCorrelationId("EVT-001");
        properties.setHeader("x-retry-count", retryCount);
        return new Message(objectMapper.writeValueAsBytes(request), properties);
    }
}
