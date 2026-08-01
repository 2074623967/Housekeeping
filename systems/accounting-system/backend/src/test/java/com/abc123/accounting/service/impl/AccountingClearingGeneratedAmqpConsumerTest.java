package com.abc123.accounting.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
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

/** 清分结果账务 AMQP 消费适配测试。 */
@ExtendWith(MockitoExtension.class)
class AccountingClearingGeneratedAmqpConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AccountingEventService accountingEventService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    @Test
    void shouldConsumeAndAckWhenAccountingSucceeds() throws Exception {
        consumer(3).consume(message(0), channel, 41L);

        verify(accountingEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));
        verify(channel).basicAck(41L, false);
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
    }

    @Test
    void shouldRouteToRetryWhenAccountingFails() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(accountingEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));

        consumer(3).consume(message(0), channel, 42L);

        ArgumentCaptor<Message> retryMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("clearing.trade.retry"), eq("clearing.generated.accounting.retry.v1"), retryMessage.capture());
        verify(channel).basicAck(42L, false);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                retryMessage.getValue().getMessageProperties().getHeaders().get("x-retry-count"));
    }

    @Test
    void shouldRouteToDeadLetterWhenAccountingRetryLimitReached() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("permanent failure"))
                .when(accountingEventService).consumeClearingGenerated(any(ClearingGeneratedEventRequestDTO.class));

        consumer(3).consume(message(3), channel, 43L);

        verify(rabbitTemplate).send(eq("clearing.trade.dlq"), eq("clearing.generated.accounting.dlq.v1"), any(Message.class));
        verify(channel).basicAck(43L, false);
    }

    private AccountingClearingGeneratedAmqpConsumer consumer(int maxRetryCount) {
        return new AccountingClearingGeneratedAmqpConsumer(
                accountingEventService,
                rabbitTemplate,
                objectMapper,
                "clearing.trade.retry",
                "clearing.generated.accounting.retry.v1",
                "clearing.trade.dlq",
                "clearing.generated.accounting.dlq.v1",
                maxRetryCount);
    }

    private Message message(int retryCount) throws Exception {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setAccountNo("ACT10002");
        request.setClearingOrderNo("CLR-001");
        request.setBizNo("PAY-001");
        request.setAmount(new BigDecimal("90.00"));
        request.setSummary("清分结果入账至服务者应收");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("EVT-001");
        properties.setCorrelationId("EVT-001");
        properties.setHeader("x-retry-count", retryCount);
        return new Message(objectMapper.writeValueAsBytes(request), properties);
    }
}
