package com.abc123.accounting.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
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

/**
 * 支付成功账务 AMQP 消费适配测试。
 */
@ExtendWith(MockitoExtension.class)
class AccountingPaymentSuccessAmqpConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AccountingEventService accountingEventService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    @Test
    void shouldConsumeAndAckWhenAccountingSucceeds() throws Exception {
        consumer(3).consume(message(0), channel, 21L);

        verify(accountingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        verify(channel).basicAck(21L, false);
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
    }

    @Test
    void shouldRouteToRetryWhenAccountingFails() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(accountingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer(3).consume(message(0), channel, 22L);

        ArgumentCaptor<Message> retryMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.accounting.retry.v1"), retryMessage.capture());
        verify(channel).basicAck(22L, false);
        org.junit.jupiter.api.Assertions.assertEquals(1, retryMessage.getValue().getMessageProperties().getHeaders().get("x-retry-count"));
    }

    @Test
    void shouldRouteToDeadLetterWhenAccountingRetryLimitReached() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("permanent failure"))
                .when(accountingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer(3).consume(message(3), channel, 23L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"), eq("payment.success.accounting.dlq.v1"), any(Message.class));
        verify(channel).basicAck(23L, false);
    }

    private AccountingPaymentSuccessAmqpConsumer consumer(int maxRetryCount) {
        return new AccountingPaymentSuccessAmqpConsumer(
                accountingEventService,
                rabbitTemplate,
                objectMapper,
                "payment.trade.retry",
                "payment.success.accounting.retry.v1",
                "payment.trade.dlq",
                "payment.success.accounting.dlq.v1",
                maxRetryCount);
    }

    private Message message(int retryCount) throws Exception {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10003");
        request.setPaymentOrderId("PAY-001");
        request.setOrderNo("ORD-001");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("168.00"));
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("EVT-001");
        properties.setCorrelationId("EVT-001");
        properties.setHeader("x-retry-count", retryCount);
        return new Message(objectMapper.writeValueAsBytes(request), properties);
    }
}
