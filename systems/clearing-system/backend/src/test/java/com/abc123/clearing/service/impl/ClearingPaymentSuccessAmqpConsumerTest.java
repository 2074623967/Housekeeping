package com.abc123.clearing.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
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
 * 支付成功 AMQP 消费适配测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingPaymentSuccessAmqpConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ClearingEventService clearingEventService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private Channel channel;

    @Test
    void shouldConsumeAndAckWhenBusinessSucceeds() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);

        consumer.consume(message(0), channel, 11L);

        verify(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        verify(channel).basicAck(11L, false);
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
    }

    @Test
    void shouldRouteToRetryAndAckOriginalMessageWhenBusinessFails() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer.consume(message(0), channel, 12L);

        ArgumentCaptor<Message> retryMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"), retryMessage.capture());
        verify(channel).basicAck(12L, false);
        org.junit.jupiter.api.Assertions.assertEquals(1, retryMessage.getValue().getMessageProperties().getHeaders().get("x-retry-count"));
    }

    @Test
    void shouldRouteToDeadLetterWhenRetryLimitReached() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("permanent failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer.consume(message(3), channel, 13L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"), eq("payment.success.clearing.dlq.v1"), any(Message.class));
        verify(channel).basicAck(13L, false);
    }

    @Test
    void shouldNackOriginalMessageWhenRetryPublishFails() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("business failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"), any(Message.class));

        consumer.consume(message(0), channel, 14L);

        verify(channel).basicNack(14L, false, true);
        verify(channel, never()).basicAck(14L, false);
    }

    private ClearingPaymentSuccessAmqpConsumer consumer(int maxRetryCount) {
        return new ClearingPaymentSuccessAmqpConsumer(
                clearingEventService,
                rabbitTemplate,
                objectMapper,
                "payment.trade.retry",
                "payment.success.clearing.retry.v1",
                "payment.trade.dlq",
                "payment.success.clearing.dlq.v1",
                maxRetryCount);
    }

    private Message message(int retryCount) throws Exception {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
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
