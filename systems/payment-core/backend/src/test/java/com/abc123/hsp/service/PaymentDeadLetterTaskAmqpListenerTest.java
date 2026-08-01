package com.abc123.hsp.service;

import static org.mockito.Mockito.verify;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

/** MQ 死信补偿 intake listener 测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentDeadLetterTaskAmqpListenerTest {

    @Mock
    private PaymentDeadLetterTaskService paymentDeadLetterTaskService;
    @Mock
    private Channel channel;

    @Test
    void shouldAckOnlyAfterTaskIsPersisted() throws Exception {
        Message message = new Message(new byte[0], new MessageProperties());

        new PaymentDeadLetterTaskAmqpListener(paymentDeadLetterTaskService)
                .consume(message, channel, 11L, "payment.success.clearing.dlq.v1");

        verify(paymentDeadLetterTaskService).intake(message, "payment.success.clearing.dlq.v1");
        verify(channel).basicAck(11L, false);
    }

    @Test
    void shouldRequeueWhenTaskPersistenceFails() throws Exception {
        Message message = new Message(new byte[0], new MessageProperties());
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(paymentDeadLetterTaskService).intake(message, "payment.success.clearing.dlq.v1");

        new PaymentDeadLetterTaskAmqpListener(paymentDeadLetterTaskService)
                .consume(message, channel, 12L, "payment.success.clearing.dlq.v1");

        verify(channel).basicNack(12L, false, true);
    }
}
