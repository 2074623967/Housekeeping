package com.abc123.clearing.service.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

final class AmqpConfirmedForwarder {

    private AmqpConfirmedForwarder() {
    }

    static void send(RabbitTemplate rabbitTemplate, String exchange, String routingKey,
                     Message message, long timeoutMs) {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        try {
            long boundedTimeoutMs = timeoutMs <= 0 ? 5000L : Math.min(timeoutMs, 60000L);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(boundedTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("AMQP 转发未被目标队列确认");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AMQP 转发确认中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("AMQP 转发确认失败", exception);
        }
    }
}
