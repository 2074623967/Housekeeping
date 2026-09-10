package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放")));
    }

    @Test
    void shouldMaskListSnapshotsWithoutChangingMapperEntity() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.findAll(any(PaymentDeadLetterTaskQueryDTO.class)))
                .thenReturn(Collections.singletonList(task));
        when(paymentDeadLetterTaskMapper.count(any(PaymentDeadLetterTaskQueryDTO.class))).thenReturn(1L);

        PageResultDTO<PaymentDeadLetterTaskEntity> result = service().list(new PaymentDeadLetterTaskQueryDTO());

        assertEquals("138****8000", readJson(result.getItems().get(0).getPayloadSnapshot())
                .at("/customer/phone").asText());
        assertEquals("[REDACTED]", readJson(result.getItems().get(0).getHeaderSnapshot())
                .get("Authorization").asText());
        org.junit.jupiter.api.Assertions.assertTrue(task.getPayloadSnapshot().contains("13800138000"));
        org.junit.jupiter.api.Assertions.assertTrue(task.getHeaderSnapshot().contains("secret-token"));
    }

    @Test
    void shouldMaskActionResponseWithoutChangingReplaySource() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReadyToReplay("DLQ-001", "支付运营", "核对完成"))
                .thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);

        PaymentDeadLetterTaskEntity result = service()
                .markReadyToReplay("DLQ-001", action("支付运营", "核对完成"));

        org.junit.jupiter.api.Assertions.assertFalse(result.getPayloadSnapshot().contains("13800138000"));
        org.junit.jupiter.api.Assertions.assertTrue(task.getPayloadSnapshot().contains("13800138000"));
    }

    @Test
    void shouldReplayOnlyTheOriginalConsumerAfterReview() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
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
        org.junit.jupiter.api.Assertions.assertTrue(
                new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8).contains("13800138000"));
        org.junit.jupiter.api.Assertions.assertFalse(messageCaptor.getValue().getMessageProperties().getHeaders()
                .containsKey("x-retry-count"));
        assertEquals("DLQ-001", result.getTaskNo());
        org.junit.jupiter.api.Assertions.assertFalse(result.getPayloadSnapshot().contains("13800138000"));
    }

    @Test
    void shouldRecordReplayFailureForManualFollowUp() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(rabbitTemplate.invoke(any())).thenThrow(new IllegalStateException("broker unavailable"));

        service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldRequireOperatorAndResolutionNoteForManualAction() {
        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", new PaymentDeadLetterTaskActionRequestDTO()));
    }

    @Test
    void shouldNormalizeManualActionBeforeUpdatingTask() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReadyToReplay("DLQ-001", "finance-ops", "核对完成"))
                .thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);

        service().markReadyToReplay(" DLQ-001 ", action(" finance-ops ", " 核对完成 "));

        verify(paymentDeadLetterTaskMapper)
                .markReadyToReplay("DLQ-001", "finance-ops", "核对完成");
    }

    @Test
    void shouldRejectInvalidManualActionBeforeMapperOrBrokerAccess() {
        String oversizedOperator = String.join("", Collections.nCopies(65, "A"));
        String oversizedNote = String.join("", Collections.nCopies(513, "N"));

        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", action(oversizedOperator, "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().markManuallyResolved("DLQ-001", action("finance-ops", oversizedNote)));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("finance\nops", "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("finance-ops", "核对\t完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001\nforged", action("finance-ops", "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay(String.join("", Collections.nCopies(65, "D")),
                        action("finance-ops", "核对完成")));

        verifyNoInteractions(paymentDeadLetterTaskMapper, rabbitTemplate);
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
        task.setPayloadSnapshot("{\"paymentOrderId\":\"PAY-001\",\"customer\":{\"phone\":\"13800138000\"}}");
        task.setHeaderSnapshot("{\"Authorization\":\"Bearer secret-token\",\"x-retry-count\":3}");
        return task;
    }

    private com.fasterxml.jackson.databind.JsonNode readJson(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
