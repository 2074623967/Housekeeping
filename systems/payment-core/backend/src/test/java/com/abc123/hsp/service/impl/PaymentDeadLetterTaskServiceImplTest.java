package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
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
        properties.setMessageId(" MSG-001 ");
        properties.setCorrelationId(" CORR-001 ");
        properties.setHeader("x-retry-count", 3);
        properties.setHeader("Authorization", "Bearer secret-token");

        service.intake(new Message("{invalid}".getBytes(java.nio.charset.StandardCharsets.UTF_8), properties),
                "payment.success.clearing.dlq.v1");

        ArgumentCaptor<PaymentDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(PaymentDeadLetterTaskEntity.class);
        verify(paymentDeadLetterTaskMapper).insertIgnore(captor.capture());
        PaymentDeadLetterTaskEntity task = captor.getValue();
        assertEquals("MSG-001", task.getMessageId());
        assertEquals("CORR-001", task.getCorrelationId());
        assertEquals("clearing-system", task.getTargetSystem());
        assertEquals("payment.trade.replay", task.getReplayExchange());
        assertEquals("payment.success.clearing.replay.v1", task.getReplayRoutingKey());
        assertEquals("PENDING_REVIEW", task.getTaskStatus());
        org.junit.jupiter.api.Assertions.assertTrue(task.getHeaderSnapshot().contains("x-retry-count"));
        assertEquals("[REDACTED]", readJson(task.getHeaderSnapshot()).get("Authorization").asText());
        org.junit.jupiter.api.Assertions.assertFalse(task.getHeaderSnapshot().contains("secret-token"));
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
        String sensitiveRoutingKey = "unknown.dlq.password=secret-token\nforged.v1";

        PaymentDeadLetterTaskService.UnsupportedDeadLetterRouteException exception = assertThrows(
                PaymentDeadLetterTaskService.UnsupportedDeadLetterRouteException.class,
                () -> service().intake(
                        new Message(new byte[0], new MessageProperties()), sensitiveRoutingKey));

        assertEquals("不支持的死信路由", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(sensitiveRoutingKey));
        verifyNoInteractions(paymentDeadLetterTaskMapper, rabbitTemplate);
    }

    @Test
    void shouldRejectInvalidDeadLetterMetadataBeforeMapperAccess() {
        MessageProperties invalidMessageIdProperties = new MessageProperties();
        invalidMessageIdProperties.setMessageId("MSG-001\n");
        MessageProperties oversizedCorrelationProperties = new MessageProperties();
        oversizedCorrelationProperties.setCorrelationId(repeat("C", 129));

        PaymentDeadLetterTaskService.InvalidDeadLetterMessageException messageIdException = assertThrows(
                PaymentDeadLetterTaskService.InvalidDeadLetterMessageException.class,
                () -> service().intake(new Message(new byte[0], invalidMessageIdProperties),
                        "payment.success.clearing.dlq.v1"));
        assertThrows(PaymentDeadLetterTaskService.InvalidDeadLetterMessageException.class,
                () -> service().intake(new Message(new byte[0], oversizedCorrelationProperties),
                        "payment.success.clearing.dlq.v1"));
        assertThrows(PaymentDeadLetterTaskService.UnsupportedDeadLetterRouteException.class,
                () -> service().intake(new Message(new byte[0], new MessageProperties()),
                        "payment.success.clearing.dlq." + repeat("R", 100) + ".v1"));

        assertEquals("死信消息元数据非法", messageIdException.getMessage());
        verifyNoInteractions(paymentDeadLetterTaskMapper, rabbitTemplate);
    }

    @Test
    void shouldRequireReviewBeforeReplay() {
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(0);

        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放")));
    }

    @Test
    void shouldReturnMaskedDetailByTaskNo() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setPayloadSnapshot("{\"token\":\"secret\"}");
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);

        PaymentDeadLetterTaskEntity detail = service().getDetail(" DLQ-001 ");

        assertEquals("DLQ-001", detail.getTaskNo());
        org.junit.jupiter.api.Assertions.assertFalse(detail.getPayloadSnapshot().contains("secret"));
        verify(paymentDeadLetterTaskMapper).findByTaskNo("DLQ-001");
    }

    @Test
    void shouldUseStableErrorCodeWhenDetailMissing() {
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-404")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service().getDetail("DLQ-404"));

        assertEquals(ErrorCode.PAYMENT_DEAD_LETTER_TASK_NOT_FOUND, exception.getCode());
        assertEquals("死信任务不存在", exception.getMessage());
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
    void shouldRejectInvalidListQueryFieldsBeforeMapperAccess() {
        PaymentDeadLetterTaskQueryDTO oversizedStatus = query(
                repeat("S", 33), null, null);
        PaymentDeadLetterTaskQueryDTO controlStatus = query(
                "PENDING_REVIEW\n", null, null);
        PaymentDeadLetterTaskQueryDTO oversizedTargetSystem = query(
                null, repeat("T", 65), null);
        PaymentDeadLetterTaskQueryDTO controlTargetSystem = query(
                null, "clearing-system\n", null);
        PaymentDeadLetterTaskQueryDTO oversizedMessageId = query(
                null, null, repeat("M", 129));
        PaymentDeadLetterTaskQueryDTO controlMessageId = query(
                null, null, "MSG-001\n");

        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedStatus));
        assertThrows(IllegalArgumentException.class, () -> service().list(controlStatus));
        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedTargetSystem));
        assertThrows(IllegalArgumentException.class, () -> service().list(controlTargetSystem));
        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedMessageId));
        assertThrows(IllegalArgumentException.class, () -> service().list(controlMessageId));
        verifyNoInteractions(paymentDeadLetterTaskMapper, rabbitTemplate);
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
    void shouldCapDeadLetterReplayPublisherConfirmTimeout() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        new PaymentDeadLetterTaskServiceImpl(
                paymentDeadLetterTaskMapper, rabbitTemplate, new ObjectMapper(), 120000L)
                .replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(rabbitTemplate).waitForConfirmsOrDie(60000L);
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
    }

    @Test
    void shouldUseDefaultDeadLetterReplayPublisherConfirmTimeoutWhenNonPositive() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        new PaymentDeadLetterTaskServiceImpl(
                paymentDeadLetterTaskMapper, rabbitTemplate, new ObjectMapper(), -1L)
                .replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
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
    void shouldRejectInvalidPersistedReplayMetadataBeforeBrokerAccess() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setReplayRoutingKey("payment.success.clearing.replay.v1\n");
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);

        PaymentDeadLetterTaskEntity result = service().replay(
                "DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verifyNoInteractions(rabbitTemplate);
        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
        assertEquals("DLQ-001", result.getTaskNo());
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

    @Test
    void shouldRejectManualActionControlCharactersBeforeTrimming() {
        IllegalArgumentException taskNoException = assertThrows(
                IllegalArgumentException.class,
                () -> service().markReadyToReplay(
                        "DLQ-001\n", action("finance-ops", "核对完成")));
        IllegalArgumentException operatorException = assertThrows(
                IllegalArgumentException.class,
                () -> service().markManuallyResolved(
                        "DLQ-001", action("finance-ops\n", "人工结案")));
        IllegalArgumentException noteException = assertThrows(
                IllegalArgumentException.class,
                () -> service().replay(
                        "DLQ-001", action("finance-ops", "允许重放\n")));

        assertEquals("死信任务号不能包含控制字符", taskNoException.getMessage());
        assertEquals("处置人不能包含控制字符", operatorException.getMessage());
        assertEquals("处置说明不能包含控制字符", noteException.getMessage());
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

    private PaymentDeadLetterTaskQueryDTO query(String taskStatus, String targetSystem, String messageId) {
        PaymentDeadLetterTaskQueryDTO query = new PaymentDeadLetterTaskQueryDTO();
        query.setTaskStatus(taskStatus);
        query.setTargetSystem(targetSystem);
        query.setMessageId(messageId);
        return query;
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
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
