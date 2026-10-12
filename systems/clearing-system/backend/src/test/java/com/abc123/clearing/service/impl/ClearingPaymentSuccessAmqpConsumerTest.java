package com.abc123.clearing.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.common.PaymentEventAmqpSignature;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
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

    @BeforeEach
    void confirmForwardedMessages() {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void shouldDeadLetterUnsignedMessageBeforeClearing() throws Exception {
        Message unsigned = message(0);
        unsigned.getMessageProperties().getHeaders().remove(PaymentEventAmqpSignature.HEADER_NAME);

        consumer(3).consume(unsigned, channel, 17L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"),
                eq("payment.success.clearing.dlq.v1"), any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(17L, false);
        verify(clearingEventService, never()).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
    }

    @Test
    void shouldDeadLetterSignedMessageWhenSecretIsMissing() throws Exception {
        consumer(3, " ").consume(message(0), channel, 20L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"),
                eq("payment.success.clearing.dlq.v1"), any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(20L, false);
        verify(clearingEventService, never()).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
    }

    @Test
    void shouldDeadLetterTamperedMessageWithoutRetry() throws Exception {
        Message signed = message(0);
        Message tampered = new Message("{\"paymentOrderId\":\"PAY-FORGED\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                signed.getMessageProperties());

        consumer(3).consume(tampered, channel, 18L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"),
                eq("payment.success.clearing.dlq.v1"), any(Message.class), any(CorrelationData.class));
        verify(rabbitTemplate, never()).send(eq("payment.trade.retry"), any(), any(Message.class), any(CorrelationData.class));
        verify(clearingEventService, never()).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
    }

    @Test
    void shouldNackWhenUntrustedDeadLetterPublishFails() throws Exception {
        Message unsigned = message(0);
        unsigned.getMessageProperties().getHeaders().remove(PaymentEventAmqpSignature.HEADER_NAME);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(eq("payment.trade.dlq"),
                        eq("payment.success.clearing.dlq.v1"), any(Message.class), any(CorrelationData.class));

        consumer(3).consume(unsigned, channel, 19L);

        verify(channel).basicNack(19L, false, true);
        verify(channel, never()).basicAck(19L, false);
    }

    @Test
    void shouldNotAckUntrustedMessageBeforeDeadLetterConfirmation() throws Exception {
        Message unsigned = message(0);
        unsigned.getMessageProperties().getHeaders().remove(PaymentEventAmqpSignature.HEADER_NAME);
        org.mockito.Mockito.doAnswer(invocation -> null).when(rabbitTemplate)
                .send(eq("payment.trade.dlq"), eq("payment.success.clearing.dlq.v1"),
                        any(Message.class), any(CorrelationData.class));
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        setConfirmTimeout(consumer, 1L);

        consumer.consume(unsigned, channel, 28L);

        verify(channel).basicNack(28L, false, true);
        verify(channel, never()).basicAck(28L, false);
    }

    @Test
    void shouldNackWhenBrokerReturnsDeadLetterDespitePositiveConfirm() throws Exception {
        Message unsigned = message(0);
        unsigned.getMessageProperties().getHeaders().remove(PaymentEventAmqpSignature.HEADER_NAME);
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(unsigned, 312, "NO_ROUTE",
                    "payment.trade.dlq", "payment.success.clearing.dlq.v1"));
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("payment.trade.dlq"),
                eq("payment.success.clearing.dlq.v1"), any(Message.class), any(CorrelationData.class));

        consumer(3).consume(unsigned, channel, 29L);

        verify(channel).basicNack(29L, false, true);
        verify(channel, never()).basicAck(29L, false);
    }

    @Test
    void shouldNackWhenBrokerNegativelyConfirmsRetry() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("business failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().set(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbitTemplate).send(eq("payment.trade.retry"),
                eq("payment.success.clearing.retry.v1"), any(Message.class), any(CorrelationData.class));

        consumer(3).consume(message(0), channel, 30L);

        verify(channel).basicNack(30L, false, true);
        verify(channel, never()).basicAck(30L, false);
    }

    @Test
    void shouldConsumeAndAckWhenBusinessSucceeds() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);

        consumer.consume(message(0), channel, 11L);

        verify(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        verify(channel).basicAck(11L, false);
        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void shouldRouteToRetryAndAckOriginalMessageWhenBusinessFails() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("temporary failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer.consume(message(0), channel, 12L);

        ArgumentCaptor<Message> retryMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"),
                retryMessage.capture(), any(CorrelationData.class));
        verify(channel).basicAck(12L, false);
        org.junit.jupiter.api.Assertions.assertEquals(1, retryMessage.getValue().getMessageProperties().getHeaders().get("x-retry-count"));
    }

    @Test
    void shouldRouteToDeadLetterWhenRetryLimitReached() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("permanent failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));

        consumer.consume(message(3), channel, 13L);

        verify(rabbitTemplate).send(eq("payment.trade.dlq"), eq("payment.success.clearing.dlq.v1"),
                any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(13L, false);
    }

    @Test
    void shouldNackOriginalMessageWhenRetryPublishFails() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        org.mockito.Mockito.doThrow(new IllegalStateException("business failure"))
                .when(clearingEventService).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"),
                        any(Message.class), any(CorrelationData.class));

        consumer.consume(message(0), channel, 14L);

        verify(channel).basicNack(14L, false, true);
        verify(channel, never()).basicAck(14L, false);
    }

    @Test
    void shouldRouteUnsupportedSchemaVersionToRetry() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setSchemaVersion("2");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("EVT-001");
        properties.setHeader("x-retry-count", 0);

        consumer.consume(signedMessage(new Message(objectMapper.writeValueAsBytes(request), properties)), channel, 15L);

        verify(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"),
                any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(15L, false);
        verify(clearingEventService, never()).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
    }

    @Test
    void shouldRouteMismatchedEventTypeHeaderToRetry() throws Exception {
        ClearingPaymentSuccessAmqpConsumer consumer = consumer(3);
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY-001");
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("EVT-001");
        properties.setHeader("eventType", "REFUND_SUCCESS");
        properties.setHeader("x-retry-count", 0);

        consumer.consume(signedMessage(new Message(objectMapper.writeValueAsBytes(request), properties)), channel, 16L);

        verify(rabbitTemplate).send(eq("payment.trade.retry"), eq("payment.success.clearing.retry.v1"),
                any(Message.class), any(CorrelationData.class));
        verify(channel).basicAck(16L, false);
        verify(clearingEventService, never()).consumePaymentSuccess(any(PaymentSuccessEventRequestDTO.class));
    }

    private ClearingPaymentSuccessAmqpConsumer consumer(int maxRetryCount) {
        return consumer(maxRetryCount, "local-payment-amqp-test-secret");
    }

    private ClearingPaymentSuccessAmqpConsumer consumer(int maxRetryCount, String secret) {
        return new ClearingPaymentSuccessAmqpConsumer(
                clearingEventService,
                rabbitTemplate,
                objectMapper,
                "payment.trade.retry",
                "payment.success.clearing.retry.v1",
                "payment.trade.dlq",
                "payment.success.clearing.dlq.v1",
                maxRetryCount,
                secret);
    }

    private void setConfirmTimeout(ClearingPaymentSuccessAmqpConsumer consumer, long timeoutMs) {
        try {
            Field field = ClearingPaymentSuccessAmqpConsumer.class.getDeclaredField("forwardConfirmTimeoutMs");
            field.setAccessible(true);
            field.setLong(consumer, timeoutMs);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
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
        return signedMessage(new Message(objectMapper.writeValueAsBytes(request), properties));
    }

    private Message signedMessage(Message message) {
        message.getMessageProperties().setHeader(PaymentEventAmqpSignature.HEADER_NAME,
                PaymentEventAmqpSignature.sign(message.getBody(), "local-payment-amqp-test-secret"));
        return message;
    }
}
