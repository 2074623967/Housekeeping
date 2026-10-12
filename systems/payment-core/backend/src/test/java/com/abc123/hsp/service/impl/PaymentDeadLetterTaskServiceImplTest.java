package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.PaymentEventAmqpSignature;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

/** MQ 死信补偿任务服务测试。 */
@ExtendWith(MockitoExtension.class)
class PaymentDeadLetterTaskServiceImplTest {

    @Mock
    private PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper;
    @Mock
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void confirmReplayedMessages() {
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
    }

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
        assertEquals(false, task.getSourceSignatureVerified());
    }

    @Test
    void shouldOnlyTrustIntactSignedPaymentAndClearingDeadLetters() {
        PaymentDeadLetterTaskServiceImpl service = service();
        MessageProperties paymentProperties = new MessageProperties();
        paymentProperties.setMessageId("MSG-PAYMENT");
        byte[] paymentBody = "{\"paymentOrderId\":\"PAY-001\"}".getBytes(StandardCharsets.UTF_8);
        paymentProperties.setHeader(PaymentEventAmqpSignature.HEADER_NAME,
                PaymentEventAmqpSignature.sign(paymentBody, "local-payment-amqp-test-secret"));
        service.intake(new Message(paymentBody, paymentProperties), "payment.success.clearing.dlq.v1");

        MessageProperties clearingProperties = new MessageProperties();
        clearingProperties.setMessageId("MSG-CLEARING");
        byte[] clearingBody = "{\"clearingNo\":\"CLR-001\"}".getBytes(StandardCharsets.UTF_8);
        clearingProperties.setHeader("X-HSP-Clearing-Event-AMQP-Signature",
                PaymentEventAmqpSignature.sign(clearingBody, "local-clearing-amqp-test-secret"));
        service.intake(new Message(clearingBody, clearingProperties), "clearing.generated.settlement.dlq.v1");
        service.intake(new Message("{\"clearingNo\":\"CLR-FORGED\"}".getBytes(StandardCharsets.UTF_8),
                clearingProperties), "clearing.generated.settlement.dlq.v1");

        ArgumentCaptor<PaymentDeadLetterTaskEntity> captor = ArgumentCaptor.forClass(PaymentDeadLetterTaskEntity.class);
        verify(paymentDeadLetterTaskMapper, org.mockito.Mockito.times(3)).insertIgnore(captor.capture());
        assertEquals(true, captor.getAllValues().get(0).getSourceSignatureVerified());
        assertEquals(true, captor.getAllValues().get(1).getSourceSignatureVerified());
        assertEquals(false, captor.getAllValues().get(2).getSourceSignatureVerified());
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
        MessageProperties oversizedAfterTrimCorrelationProperties = new MessageProperties();
        oversizedAfterTrimCorrelationProperties.setCorrelationId(repeat("C", 128) + " ");

        PaymentDeadLetterTaskService.InvalidDeadLetterMessageException messageIdException = assertThrows(
                PaymentDeadLetterTaskService.InvalidDeadLetterMessageException.class,
                () -> service().intake(new Message(new byte[0], invalidMessageIdProperties),
                        "payment.success.clearing.dlq.v1"));
        assertThrows(PaymentDeadLetterTaskService.InvalidDeadLetterMessageException.class,
                () -> service().intake(new Message(new byte[0], oversizedCorrelationProperties),
                        "payment.success.clearing.dlq.v1"));
        assertThrows(PaymentDeadLetterTaskService.InvalidDeadLetterMessageException.class,
                () -> service().intake(new Message(new byte[0], oversizedAfterTrimCorrelationProperties),
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
    void shouldUseReplayClaimForStaleInProgressTask() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "接管超时未完成的重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(1);

        PaymentDeadLetterTaskEntity result = service().replay(
                "DLQ-001", action("支付运营", "接管超时未完成的重放"));

        assertEquals("DLQ-001", result.getTaskNo());
        verify(paymentDeadLetterTaskMapper).markReplaying(
                "DLQ-001", "支付运营", "接管超时未完成的重放");
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
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
                .thenReturn(java.util.Arrays.asList((PaymentDeadLetterTaskEntity) null, task));
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
        PaymentDeadLetterTaskQueryDTO oversizedAfterTrimMessageId = query(
                null, null, repeat("M", 128) + " ");
        PaymentDeadLetterTaskQueryDTO controlMessageId = query(
                null, null, "MSG-001\n");

        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedStatus));
        assertThrows(IllegalArgumentException.class, () -> service().list(controlStatus));
        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedTargetSystem));
        assertThrows(IllegalArgumentException.class, () -> service().list(controlTargetSystem));
        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedMessageId));
        assertThrows(IllegalArgumentException.class, () -> service().list(oversizedAfterTrimMessageId));
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
    void shouldRejectReadyToReplayWhenUpdateAffectsMultipleRows() {
        when(paymentDeadLetterTaskMapper.markReadyToReplay("DLQ-001", "支付运营", "核对完成"))
                .thenReturn(2);

        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", action("支付运营", "核对完成")));

        verify(paymentDeadLetterTaskMapper, org.mockito.Mockito.never()).findByTaskNo("DLQ-001");
    }

    @Test
    void shouldReplayOnlyTheOriginalConsumerAfterReview() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(1);

        PaymentDeadLetterTaskEntity result = service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("payment.trade.replay"), eq("payment.success.clearing.replay.v1"),
                messageCaptor.capture(), any(CorrelationData.class));
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
        assertEquals("MSG-001", messageCaptor.getValue().getMessageProperties().getMessageId());
        org.junit.jupiter.api.Assertions.assertTrue(
                new String(messageCaptor.getValue().getBody(), StandardCharsets.UTF_8).contains("13800138000"));
        org.junit.jupiter.api.Assertions.assertFalse(messageCaptor.getValue().getMessageProperties().getHeaders()
                .containsKey("x-retry-count"));
        assertEquals(PaymentEventAmqpSignature.sign(messageCaptor.getValue().getBody(), "local-payment-amqp-test-secret"),
                messageCaptor.getValue().getMessageProperties().getHeaders().get(PaymentEventAmqpSignature.HEADER_NAME));
        assertEquals("DLQ-001", result.getTaskNo());
        org.junit.jupiter.api.Assertions.assertFalse(result.getPayloadSnapshot().contains("13800138000"));
    }

    @Test
    void shouldNotCloseReplayWhenBrokerReturnsUnroutableMessage() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]"))
                .thenReturn(1);
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE",
                    "payment.trade.replay", "payment.success.clearing.replay.v1"));
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("payment.trade.replay"),
                eq("payment.success.clearing.replay.v1"), any(Message.class), any(CorrelationData.class));

        service().replay("DLQ-001", action("支付运营", "核对完成"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
        verify(paymentDeadLetterTaskMapper, never()).markReplayed("DLQ-001");
    }

    @Test
    void shouldNotCloseReplayWhenBrokerNegativelyConfirms() {
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(replayTask());
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]"))
                .thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().set(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbitTemplate).send(eq("payment.trade.replay"),
                eq("payment.success.clearing.replay.v1"), any(Message.class), any(CorrelationData.class));

        service().replay("DLQ-001", action("支付运营", "核对完成"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
        verify(paymentDeadLetterTaskMapper, never()).markReplayed("DLQ-001");
    }

    @Test
    void shouldNotCloseReplayBeforeConfirmationTimeout() {
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(replayTask());
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]"))
                .thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> null).when(rabbitTemplate)
                .send(eq("payment.trade.replay"), eq("payment.success.clearing.replay.v1"),
                        any(Message.class), any(CorrelationData.class));

        service(1L).replay("DLQ-001", action("支付运营", "核对完成"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
        verify(paymentDeadLetterTaskMapper, never()).markReplayed("DLQ-001");
    }

    @Test
    void shouldSignClearingDeadLetterReplayWithClearingSecret() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setDeadLetterRoutingKey("clearing.generated.settlement.dlq.v1");
        task.setReplayExchange("clearing.trade.replay");
        task.setReplayRoutingKey("clearing.generated.settlement.replay.v1");
        task.setTargetSystem("settlement-system");
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(1);

        service().replay("DLQ-001", action("支付运营", "核对完成"));

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("clearing.trade.replay"),
                eq("clearing.generated.settlement.replay.v1"), messageCaptor.capture(), any(CorrelationData.class));
        assertEquals(PaymentEventAmqpSignature.sign(messageCaptor.getValue().getBody(),
                        "local-clearing-amqp-test-secret"),
                messageCaptor.getValue().getMessageProperties().getHeaders()
                        .get("X-HSP-Clearing-Event-AMQP-Signature"));
    }

    @Test
    void shouldNotElevateUnsignedDeadLetterDuringManualReplay() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setSourceSignatureVerified(false);
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

        service().replay("DLQ-001", action("支付运营", "核对完成"));

        verifyNoInteractions(rabbitTemplate);
        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldRejectReplayWhenStoredTargetDiffersFromDeadLetterRoute() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setReplayRoutingKey("payment.success.accounting.replay.v1");
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

        service().replay("DLQ-001", action("支付运营", "核对完成"));

        verifyNoInteractions(rabbitTemplate);
        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldNotReplayWithoutCurrentSigningSecret() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying("DLQ-001", "支付运营", "核对完成")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);
        PaymentDeadLetterTaskServiceImpl service = service();
        setField(service, "paymentSuccessAmqpSecret", " ");

        service.replay("DLQ-001", action("支付运营", "核对完成"));

        verifyNoInteractions(rabbitTemplate);
        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldCapDeadLetterReplayPublisherConfirmTimeout() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(1);
        PaymentDeadLetterTaskServiceImpl service = service(120000L);
        service.replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        assertEquals(60000L, getField(service, "publisherConfirmTimeoutMs"));
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
    }

    @Test
    void shouldUseDefaultDeadLetterReplayPublisherConfirmTimeoutWhenNonPositive() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(1);
        PaymentDeadLetterTaskServiceImpl service = service(-1L);
        service.replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        assertEquals(5000L, getField(service, "publisherConfirmTimeoutMs"));
        verify(paymentDeadLetterTaskMapper).markReplayed("DLQ-001");
    }

    @Test
    void shouldRecordReplayFailureForManualFollowUp() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

        service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldFailWhenReplayFailureStatusCannotBePersistedExactlyOnce() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class), any(CorrelationData.class));
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放")));

        assertEquals("死信重放失败状态更新未恰好影响一行", exception.getMessage());
        assertEquals("broker unavailable", exception.getSuppressed()[0].getMessage());
    }

    @Test
    void shouldRecordReplayFailureWhenReplayStateCannotBeClosed() {
        PaymentDeadLetterTaskEntity task = replayTask();
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayed("DLQ-001")).thenReturn(0);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

        service().replay("DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
    }

    @Test
    void shouldCloseReplayWhenSnapshotReadFailsAfterClaim() {
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001"))
                .thenThrow(new IllegalStateException("temporary read failure"))
                .thenReturn(replayTask());
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

        PaymentDeadLetterTaskEntity result = service().replay(
                "DLQ-001", action("支付运营", "已核对主数据，允许定向重放"));

        verify(paymentDeadLetterTaskMapper).markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]");
        assertEquals("DLQ-001", result.getTaskNo());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void shouldRejectInvalidPersistedReplayMetadataBeforeBrokerAccess() {
        PaymentDeadLetterTaskEntity task = replayTask();
        task.setReplayRoutingKey("payment.success.clearing.replay.v1\n");
        when(paymentDeadLetterTaskMapper.markReplaying(
                "DLQ-001", "支付运营", "已核对主数据，允许定向重放")).thenReturn(1);
        when(paymentDeadLetterTaskMapper.findByTaskNo("DLQ-001")).thenReturn(task);
        when(paymentDeadLetterTaskMapper.markReplayFailed("DLQ-001", "重放发送失败：[REDACTED]")).thenReturn(1);

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
        String oversizedAfterTrimOperator = String.join("", Collections.nCopies(64, "A")) + " ";
        String oversizedAfterTrimNote = String.join("", Collections.nCopies(512, "N")) + " ";

        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", action(oversizedOperator, "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().markManuallyResolved("DLQ-001", action("finance-ops", oversizedNote)));
        assertThrows(IllegalArgumentException.class,
                () -> service().markReadyToReplay("DLQ-001", action(oversizedAfterTrimOperator, "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().markManuallyResolved("DLQ-001", action("finance-ops", oversizedAfterTrimNote)));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("finance\nops", "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001", action("finance-ops", "核对\t完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay("DLQ-001\nforged", action("finance-ops", "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay(String.join("", Collections.nCopies(65, "D")),
                        action("finance-ops", "核对完成")));
        assertThrows(IllegalArgumentException.class,
                () -> service().replay(String.join("", Collections.nCopies(64, "D")) + " ",
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
        return service(5000L);
    }

    private PaymentDeadLetterTaskServiceImpl service(long timeoutMs) {
        PaymentDeadLetterTaskServiceImpl service = new PaymentDeadLetterTaskServiceImpl(
                paymentDeadLetterTaskMapper, rabbitTemplate, new ObjectMapper(), timeoutMs);
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        setField(service, "clearingGeneratedAmqpSecret", "local-clearing-amqp-test-secret");
        return service;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Object getField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
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
        task.setDeadLetterRoutingKey("payment.success.clearing.dlq.v1");
        task.setTargetSystem("clearing-system");
        task.setSourceSignatureVerified(true);
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
