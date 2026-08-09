package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** MQ 死信补偿任务服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentDeadLetterTaskServiceImplTest {

    @Mock
    private PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void shouldCreateTraceableTaskForDedicatedDeadLetterRoute() {
        PaymentDeadLetterTaskServiceImpl service = service();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("MSG-001");
        properties.setCorrelationId("CORR-001");
        properties.setHeader("x-retry-count", 3);

        service.intake(new Message("{invalid}".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties),
                "payment.success.clearing.dlq.v1");

        ArgumentCaptor<PaymentDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(PaymentDeadLetterTaskEntity.class);
        verify(paymentDeadLetterTaskMapper).insertIgnore(captor.capture());
        PaymentDeadLetterTaskEntity task = captor.getValue();
        assertEquals("MSG-001", task.getMessageId());
        assertEquals("clearing-system", task.getTargetSystem());
        assertEquals("payment.trade.replay", task.getReplayExchange());
        assertEquals("payment.success.clearing.replay.v1", task.getReplayRoutingKey());
        assertEquals("PENDING_REVIEW", task.getTaskStatus());
        org.junit.jupiter.api.Assertions.assertTrue(task.getHeaderSnapshot().contains("x-retry-count"));
    }

    @Test
    void shouldCreateTraceableTaskForIsolatedDeadLetterRoute() {
        PaymentDeadLetterTaskServiceImpl service = service();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId("MSG-ISO-001");
        properties.setCorrelationId("CORR-ISO-001");

        service.intake(new Message("{invalid}".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties),
                "payment.success.clearing.dlq.20260803a.v1");

        ArgumentCaptor<PaymentDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(PaymentDeadLetterTaskEntity.class);
        verify(paymentDeadLetterTaskMapper).insertIgnore(captor.capture());
        PaymentDeadLetterTaskEntity task = captor.getValue();
        assertEquals("MSG-ISO-001", task.getMessageId());
        assertEquals("clearing-system", task.getTargetSystem());
        assertEquals("payment.trade.replay", task.getReplayExchange());
        assertEquals("payment.success.clearing.replay.20260803a.v1", task.getReplayRoutingKey());
        assertEquals("PENDING_REVIEW", task.getTaskStatus());
    }

    @Test
    void shouldRejectUnsupportedDeadLetterRoute() {
        assertThrows(IllegalArgumentException.class,
                () -> service().intake(new Message(new byte[0], new MessageProperties()), "unknown.dlq.v1"));
    }

    @Test
    void shouldRequireReviewBeforeReplay() {
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营")).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放")));
    }

    @Test
    void shouldReplayOnlyTheOriginalConsumerAfterReview() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        PaymentDeadLetterTaskEntity result = service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("payment.trade.replay"), eq("payment.success.clearing.replay.v1"), messageCaptor.capture());
        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
        assertEquals("MSG-001", messageCaptor.getValue().getMessageProperties().getMessageId());
        org.junit.jupiter.api.Assertions.assertFalse(messageCaptor.getValue().getMessageProperties().getHeaders()
                .containsKey("x-retry-count"));
        assertEquals("DLQ-001", result.getTaskNo());
    }

    @Test
    void shouldRecordReplayFailureForManualFollowUp() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(rabbitTemplate.invoke(any())).thenThrow(new IllegalStateException("broker unavailable"));

        service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed(eq("DLQ-001"), org.mockito.ArgumentMatchers.contains("broker unavailable"));
    }

    @Test
    void shouldRequireOperatorAndResolutionNoteForManualAction() {
        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", new PaymentDeadLetterTaskActionRequestDTO()));
    }

    private PaymentDeadLetterTaskServiceImpl service() {
        return new PaymentDeadLetterTaskServiceImpl(paymentDeadLetterTaskMapper, rabbitTemplate, new ObjectMapper(), 5000L);
    }

    private PaymentDeadLetterTaskActionRequestDTO action(String operator, String resolutionNote) {
        PaymentDeadLetterTaskActionRequestDTO request = new PaymentDeadLetterTaskActionRequestDTO();
        request.setOperator(operator);
        request.setResolutionNote(resolutionNote);
        return request;
    }

    private PaymentDeadLetterTaskEntity replayTask() {
        PaymentDeadLetterTaskEntity task = new PaymentDeadLetterTaskEntity();
        task.setTaskNo("DLQ-001");
        task.setMessageId("MSG-001");
        task.setCorrelationId("CORR-001");
        task.setReplayExchange("payment.trade.replay");
        task.setReplayRoutingKey("payment.success.clearing.replay.v1");
        task.setPayloadSnapshot("{\"paymentOrderId\":\"PAY-001\"}");
        return task;
    }
}
