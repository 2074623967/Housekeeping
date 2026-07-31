package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 消费 payment-core 发布的支付成功事件，并复用既有清分幂等服务。
 */
@Component
@ConditionalOnProperty(name = "clearing.amqp.enabled", havingValue = "true")
public class ClearingPaymentSuccessAmqpConsumer {

    private static final String RETRY_COUNT_HEADER = "x-retry-count";

    private final ClearingEventService clearingEventService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String retryExchange;
    private final String retryRoutingKey;
    private final String deadLetterExchange;
    private final String deadLetterRoutingKey;
    private final int maxRetryCount;

    public ClearingPaymentSuccessAmqpConsumer(
            ClearingEventService clearingEventService,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${clearing.amqp.retry-exchange:payment.trade.retry}") String retryExchange,
            @Value("${clearing.amqp.retry-routing-key:payment.success.retry.v1}") String retryRoutingKey,
            @Value("${clearing.amqp.dead-letter-exchange:payment.trade.dlq}") String deadLetterExchange,
            @Value("${clearing.amqp.dead-letter-routing-key:payment.success.dlq.v1}") String deadLetterRoutingKey,
            @Value("${clearing.amqp.max-retry-count:3}") int maxRetryCount) {
        this.clearingEventService = clearingEventService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.retryExchange = retryExchange;
        this.retryRoutingKey = retryRoutingKey;
        this.deadLetterExchange = deadLetterExchange;
        this.deadLetterRoutingKey = deadLetterRoutingKey;
        this.maxRetryCount = maxRetryCount <= 0 ? 3 : maxRetryCount;
    }

    @RabbitListener(queues = "${clearing.amqp.payment-success-queue:clearing.payment-success}", ackMode = "MANUAL")
    public void consume(Message message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            PaymentSuccessEventRequestDTO request = objectMapper.readValue(message.getBody(), PaymentSuccessEventRequestDTO.class);
            clearingEventService.consumePaymentSuccess(request);
        } catch (Exception exception) {
            routeFailure(message, channel, deliveryTag);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void routeFailure(Message message, Channel channel, long deliveryTag) throws IOException {
        int retryCount = retryCount(message.getMessageProperties().getHeaders());
        try {
            if (retryCount >= maxRetryCount) {
                rabbitTemplate.send(deadLetterExchange, deadLetterRoutingKey, copyWithRetryCount(message, retryCount));
            } else {
                rabbitTemplate.send(retryExchange, retryRoutingKey, copyWithRetryCount(message, retryCount + 1));
            }
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private int retryCount(Map<String, Object> headers) {
        Object value = headers.get(RETRY_COUNT_HEADER);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private Message copyWithRetryCount(Message source, int retryCount) {
        MessageProperties properties = new MessageProperties();
        MessageProperties sourceProperties = source.getMessageProperties();
        properties.setContentType(sourceProperties.getContentType());
        properties.setMessageId(sourceProperties.getMessageId());
        properties.setCorrelationId(sourceProperties.getCorrelationId());
        properties.getHeaders().putAll(sourceProperties.getHeaders());
        properties.setHeader(RETRY_COUNT_HEADER, retryCount);
        return new Message(source.getBody(), properties);
    }
}
