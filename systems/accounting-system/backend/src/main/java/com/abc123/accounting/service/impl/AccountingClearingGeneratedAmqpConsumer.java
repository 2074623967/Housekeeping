package com.abc123.accounting.service.impl;

import com.abc123.accounting.common.ClearingEventAmqpSignature;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
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

/** 消费清分结果事件并复用账务服务的流水幂等护栏。 */
@Component
@ConditionalOnProperty(name = "accounting.amqp.enabled", havingValue = "true")
public class AccountingClearingGeneratedAmqpConsumer {

    private static final String RETRY_COUNT_HEADER = "x-retry-count";
    private static final String EVENT_TYPE_HEADER = "eventType";
    private static final String SCHEMA_VERSION_HEADER = "schemaVersion";
    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String CLEARING_GENERATED_EVENT_TYPE = "CLEARING_GENERATED";
    private static final String CLEARING_GENERATED_SCHEMA_VERSION = "1";

    private final AccountingEventService accountingEventService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String retryExchange;
    private final String retryRoutingKey;
    private final String deadLetterExchange;
    private final String deadLetterRoutingKey;
    private final int maxRetryCount;
    private final String clearingGeneratedAmqpSecret;

    public AccountingClearingGeneratedAmqpConsumer(
            AccountingEventService accountingEventService,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${accounting.amqp.clearing-generated-retry-exchange:clearing.trade.retry}") String retryExchange,
            @Value("${accounting.amqp.clearing-generated-retry-routing-key:clearing.generated.accounting.retry.v1}") String retryRoutingKey,
            @Value("${accounting.amqp.clearing-generated-dead-letter-exchange:clearing.trade.dlq}") String deadLetterExchange,
            @Value("${accounting.amqp.clearing-generated-dead-letter-routing-key:clearing.generated.accounting.dlq.v1}") String deadLetterRoutingKey,
            @Value("${accounting.amqp.max-retry-count:3}") int maxRetryCount,
            @Value("${HSP_CLEARING_GENERATED_AMQP_SECRET:}") String clearingGeneratedAmqpSecret) {
        this.accountingEventService = accountingEventService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.retryExchange = retryExchange;
        this.retryRoutingKey = retryRoutingKey;
        this.deadLetterExchange = deadLetterExchange;
        this.deadLetterRoutingKey = deadLetterRoutingKey;
        this.maxRetryCount = maxRetryCount <= 0 ? 3 : maxRetryCount;
        this.clearingGeneratedAmqpSecret = clearingGeneratedAmqpSecret;
    }

    @RabbitListener(queues = "${accounting.amqp.clearing-generated-queue:accounting.clearing-generated}", ackMode = "MANUAL")
    public void consume(Message message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (!ClearingEventAmqpSignature.matches(message.getBody(),
                message.getMessageProperties().getHeaders().get(ClearingEventAmqpSignature.HEADER_NAME),
                clearingGeneratedAmqpSecret)) {
            routeUntrusted(message, channel, deliveryTag);
            return;
        }
        try {
            ClearingGeneratedEventRequestDTO request = objectMapper.readValue(
                    message.getBody(), ClearingGeneratedEventRequestDTO.class);
            validateEnvelope(message, request);
            accountingEventService.consumeClearingGenerated(request);
        } catch (Exception exception) {
            routeFailure(message, channel, deliveryTag);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void routeUntrusted(Message message, Channel channel, long deliveryTag) throws IOException {
        try {
            rabbitTemplate.send(deadLetterExchange, deadLetterRoutingKey, message);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private void validateEnvelope(Message message, ClearingGeneratedEventRequestDTO request) {
        validateOptionalValue(request.getEventType(), CLEARING_GENERATED_EVENT_TYPE, "eventType");
        validateOptionalValue(request.getSchemaVersion(), CLEARING_GENERATED_SCHEMA_VERSION, "schemaVersion");
        validateHeader(message, EVENT_TYPE_HEADER, request.getEventType(), CLEARING_GENERATED_EVENT_TYPE);
        validateHeader(message, SCHEMA_VERSION_HEADER, request.getSchemaVersion(), CLEARING_GENERATED_SCHEMA_VERSION);
        validateHeader(message, MESSAGE_ID_HEADER, request.getEventNo(), null);
    }

    private void validateOptionalValue(String actual, String expected, String fieldName) {
        if (actual != null && !actual.trim().isEmpty() && !expected.equals(actual.trim())) {
            throw new IllegalArgumentException("清分结果事件" + fieldName + "不支持");
        }
    }

    private void validateHeader(
            Message message, String headerName, String bodyValue, String expectedValue) {
        Object headerValue = MESSAGE_ID_HEADER.equals(headerName)
                ? message.getMessageProperties().getMessageId()
                : message.getMessageProperties().getHeaders().get(headerName);
        if (headerValue == null || String.valueOf(headerValue).trim().isEmpty()) {
            return;
        }
        String normalizedHeader = String.valueOf(headerValue).trim();
        if (expectedValue != null && !expectedValue.equals(normalizedHeader)) {
            throw new IllegalArgumentException("清分结果事件消息头不支持");
        }
        if (bodyValue != null && !bodyValue.trim().isEmpty() && !bodyValue.trim().equals(normalizedHeader)) {
            throw new IllegalArgumentException("清分结果事件消息头与载荷不一致");
        }
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
