package com.abc123.hsp.service;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/** 将 broker DLQ 的副本写入 payment-core 补偿任务账本。 */
@Component
@ConditionalOnProperty(name = "payment.amqp.dead-letter-task-enabled", havingValue = "true")
public class PaymentDeadLetterTaskAmqpListener {

    private final PaymentDeadLetterTaskService paymentDeadLetterTaskService;

    public PaymentDeadLetterTaskAmqpListener(PaymentDeadLetterTaskService paymentDeadLetterTaskService) {
        this.paymentDeadLetterTaskService = paymentDeadLetterTaskService;
    }

    @RabbitListener(queues = "${payment.amqp.dead-letter-task-queue:payment.compensation.dlq-intake}", ackMode = "MANUAL")
    public void consume(Message message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws IOException {
        try {
            paymentDeadLetterTaskService.intake(message, routingKey);
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
