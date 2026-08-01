package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.service.SettlementEventService;
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

/** Consumes clearing-generated events and delegates settlement creation to the idempotent service. */
@Component
@ConditionalOnProperty(name = "settlement.amqp.enabled", havingValue = "true")
public class SettlementClearingGeneratedAmqpConsumer {
    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    private final SettlementEventService settlementEventService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String retryExchange;
    private final String retryRoutingKey;
    private final String deadLetterExchange;
    private final String deadLetterRoutingKey;
    private final int maxRetryCount;

    public SettlementClearingGeneratedAmqpConsumer(SettlementEventService settlementEventService, RabbitTemplate rabbitTemplate,
                                                    ObjectMapper objectMapper,
                                                    @Value("${settlement.amqp.retry-exchange:clearing.trade.retry}") String retryExchange,
                                                    @Value("${settlement.amqp.retry-routing-key:clearing.generated.settlement.retry.v1}") String retryRoutingKey,
                                                    @Value("${settlement.amqp.dead-letter-exchange:clearing.trade.dlq}") String deadLetterExchange,
                                                    @Value("${settlement.amqp.dead-letter-routing-key:clearing.generated.settlement.dlq.v1}") String deadLetterRoutingKey,
                                                    @Value("${settlement.amqp.max-retry-count:3}") int maxRetryCount) {
        this.settlementEventService = settlementEventService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.retryExchange = retryExchange;
        this.retryRoutingKey = retryRoutingKey;
        this.deadLetterExchange = deadLetterExchange;
        this.deadLetterRoutingKey = deadLetterRoutingKey;
        this.maxRetryCount = maxRetryCount <= 0 ? 3 : maxRetryCount;
    }

    @RabbitListener(queues = "${settlement.amqp.clearing-generated-queue:settlement.clearing-generated}", ackMode = "MANUAL")
    public void consume(Message message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            settlementEventService.consumeClearingGenerated(objectMapper.readValue(message.getBody(), ClearingGeneratedEventRequestDTO.class));
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
                rabbitTemplate.send(deadLetterExchange, deadLetterRoutingKey, copy(message, retryCount));
            } else {
                rabbitTemplate.send(retryExchange, retryRoutingKey, copy(message, retryCount + 1));
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

    private Message copy(Message source, int retryCount) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(source.getMessageProperties().getContentType());
        properties.setMessageId(source.getMessageProperties().getMessageId());
        properties.setCorrelationId(source.getMessageProperties().getCorrelationId());
        properties.getHeaders().putAll(source.getMessageProperties().getHeaders());
        properties.setHeader(RETRY_COUNT_HEADER, retryCount);
        return new Message(source.getBody(), properties);
    }
}
