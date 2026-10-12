package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.common.PaymentEventAmqpSignature;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 将消费者死信沉淀为可人工处置、可定向重放的任务账本。
 */
@Service
public class PaymentDeadLetterTaskServiceImpl implements PaymentDeadLetterTaskService {

    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_READY_TO_REPLAY = "READY_TO_REPLAY";
    private static final String STATUS_MANUAL_RESOLVED = "MANUAL_RESOLVED";
    private static final String STATUS_REPLAYED = "REPLAYED";
    private static final int TASK_NO_MAX_LENGTH = 64;
    private static final int TASK_STATUS_MAX_LENGTH = 32;
    private static final int TARGET_SYSTEM_MAX_LENGTH = 64;
    private static final int MESSAGE_ID_MAX_LENGTH = 128;
    private static final int CORRELATION_ID_MAX_LENGTH = 128;
    private static final int ROUTING_KEY_MAX_LENGTH = 128;
    private static final int EXCHANGE_MAX_LENGTH = 128;
    private static final int OPERATOR_MAX_LENGTH = 64;
    private static final int RESOLUTION_NOTE_MAX_LENGTH = 512;
    private static final int DEFAULT_CONFIRM_TIMEOUT_MS = 5000;
    private static final int MAX_CONFIRM_TIMEOUT_MS = 60000;
    private static final String CLEARING_SIGNATURE_HEADER = "X-HSP-Clearing-Event-AMQP-Signature";
    private static final Pattern PAYMENT_DLQ_PATTERN = Pattern.compile(
            "^payment\\.success\\.(clearing|accounting)\\.dlq(?:\\.([^.]+))?\\.v1$");
    private static final Pattern CLEARING_DLQ_PATTERN = Pattern.compile(
            "^clearing\\.generated\\.(settlement|accounting)\\.dlq(?:\\.([^.]+))?\\.v1$");

    private final PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;
    private final long publisherConfirmTimeoutMs;

    @Value("${HSP_PAYMENT_SUCCESS_AMQP_SECRET:}")
    private String paymentSuccessAmqpSecret;

    @Value("${HSP_CLEARING_GENERATED_AMQP_SECRET:}")
    private String clearingGeneratedAmqpSecret;

    @Autowired
    public PaymentDeadLetterTaskServiceImpl(PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper,
                                            RabbitTemplate rabbitTemplate,
                                            ObjectMapper objectMapper,
                                            PaymentSensitiveDataMasker sensitiveDataMasker,
                                            @Value("${payment.amqp.publisher-confirm-timeout-ms:5000}")
                                            long publisherConfirmTimeoutMs) {
        this.paymentDeadLetterTaskMapper = paymentDeadLetterTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.publisherConfirmTimeoutMs = normalizePublisherConfirmTimeoutMs(publisherConfirmTimeoutMs);
    }

    PaymentDeadLetterTaskServiceImpl(PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper,
                                     RabbitTemplate rabbitTemplate,
                                     ObjectMapper objectMapper,
                                     long publisherConfirmTimeoutMs) {
        this(paymentDeadLetterTaskMapper, rabbitTemplate, objectMapper,
                new PaymentSensitiveDataMasker(objectMapper), publisherConfirmTimeoutMs);
    }

    @Override
    public void intake(Message message, String deadLetterRoutingKey) {
        if (message == null || message.getBody() == null || message.getMessageProperties() == null) {
            throw new PaymentDeadLetterTaskService.InvalidDeadLetterMessageException();
        }
        String normalizedDeadLetterRoutingKey = normalizeDeadLetterRoutingKey(deadLetterRoutingKey);
        ReplayRoute replayRoute = ReplayRoute.fromDeadLetterRoutingKey(normalizedDeadLetterRoutingKey);
        if (replayRoute == null) {
            throw new PaymentDeadLetterTaskService.UnsupportedDeadLetterRouteException();
        }
        MessageProperties properties = message.getMessageProperties();
        PaymentDeadLetterTaskEntity entity = new PaymentDeadLetterTaskEntity();
        entity.setTaskNo("DLQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        entity.setMessageId(resolveMessageId(message));
        entity.setCorrelationId(normalizeOptionalMessageMetadata(
                properties.getCorrelationId(), CORRELATION_ID_MAX_LENGTH));
        entity.setDeadLetterRoutingKey(normalizedDeadLetterRoutingKey);
        entity.setTargetSystem(replayRoute.targetSystem);
        entity.setReplayExchange(replayRoute.replayExchange);
        entity.setReplayRoutingKey(replayRoute.replayRoutingKey);
        entity.setTaskStatus(STATUS_PENDING_REVIEW);
        entity.setTaskStatusType("warn");
        entity.setPayloadSnapshot(new String(message.getBody(), StandardCharsets.UTF_8));
        entity.setHeaderSnapshot(sensitiveDataMasker.maskPayload(writeHeaders(properties.getHeaders())));
        entity.setSourceSignatureVerified(isSourceSignatureVerified(message, replayRoute));
        entity.setReplayCount(0);
        paymentDeadLetterTaskMapper.insertIgnore(entity);
    }

    @Override
    public PageResultDTO<PaymentDeadLetterTaskEntity> list(PaymentDeadLetterTaskQueryDTO query) {
        PaymentDeadLetterTaskQueryDTO normalizedQuery = normalizeQuery(query);
        return new PageResultDTO<PaymentDeadLetterTaskEntity>(
                maskTasks(paymentDeadLetterTaskMapper.findAll(normalizedQuery)),
                paymentDeadLetterTaskMapper.count(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize());
    }

    @Override
    public PaymentDeadLetterTaskEntity getDetail(String taskNo) {
        return maskedTaskCopy(findRequiredTask(taskNo));
    }

    @Override
    public PaymentDeadLetterTaskEntity markReadyToReplay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        if (paymentDeadLetterTaskMapper.markReadyToReplay(normalizeTaskNo(taskNo), normalizedRequest.getOperator(),
                normalizedRequest.getResolutionNote()) != 1) {
            throw new IllegalArgumentException("死信任务不存在或当前状态不允许重放");
        }
        return maskedTaskCopy(findRequiredTask(taskNo));
    }

    @Override
    public PaymentDeadLetterTaskEntity markManuallyResolved(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        if (paymentDeadLetterTaskMapper.markManuallyResolved(normalizeTaskNo(taskNo), normalizedRequest.getOperator(),
                normalizedRequest.getResolutionNote()) != 1) {
            throw new IllegalArgumentException("死信任务不存在或当前状态不允许人工结案");
        }
        return maskedTaskCopy(findRequiredTask(taskNo));
    }

    @Override
    public PaymentDeadLetterTaskEntity replay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        String normalizedTaskNo = normalizeTaskNo(taskNo);
        if (paymentDeadLetterTaskMapper.markReplaying(
                normalizedTaskNo,
                normalizedRequest.getOperator(),
                normalizedRequest.getResolutionNote()) != 1) {
            throw new IllegalArgumentException("死信任务尚未完成复核或已被其他操作处理");
        }
        try {
            PaymentDeadLetterTaskEntity task = findRequiredTask(normalizedTaskNo);
            final String replayExchange = normalizeReplayRequired(
                    task.getReplayExchange(), EXCHANGE_MAX_LENGTH);
            final String replayRoutingKey = normalizeReplayRequired(
                    task.getReplayRoutingKey(), ROUTING_KEY_MAX_LENGTH);
            final String replayMessageId = normalizeReplayRequired(
                    task.getMessageId(), MESSAGE_ID_MAX_LENGTH);
            final String replayCorrelationId = normalizeReplayOptional(
                    task.getCorrelationId(), CORRELATION_ID_MAX_LENGTH);
            if (!normalizedTaskNo.equals(normalizeReplayRequired(task.getTaskNo(), TASK_NO_MAX_LENGTH))
                    || task.getPayloadSnapshot() == null) {
                throw new IllegalArgumentException("死信重放快照非法");
            }
            ReplayRoute expectedRoute = ReplayRoute.fromDeadLetterRoutingKey(task.getDeadLetterRoutingKey());
            if (!Boolean.TRUE.equals(task.getSourceSignatureVerified()) || expectedRoute == null
                    || !expectedRoute.replayExchange.equals(replayExchange)
                    || !expectedRoute.replayRoutingKey.equals(replayRoutingKey)
                    || !expectedRoute.targetSystem.equals(task.getTargetSystem())) {
                throw new IllegalArgumentException("死信来源未认证或重放路由不一致");
            }
            final String signatureSecret = signatureSecret(expectedRoute);
            if (!StringUtils.hasText(signatureSecret)) {
                throw new IllegalStateException("死信重放签名密钥未配置");
            }
            sendConfirmedReplay(replayExchange, replayRoutingKey,
                    buildReplayMessage(task.getPayloadSnapshot(), normalizedTaskNo,
                            replayMessageId, replayCorrelationId, expectedRoute, signatureSecret));
            if (paymentDeadLetterTaskMapper.markReplayed(normalizedTaskNo) != 1) {
                throw new IllegalStateException("死信重放状态更新失败");
            }
        } catch (RuntimeException exception) {
            int failedStatusUpdated = paymentDeadLetterTaskMapper.markReplayFailed(
                    normalizedTaskNo, buildFailureNote());
            if (failedStatusUpdated != 1) {
                IllegalStateException statusException = new IllegalStateException(
                        "死信重放失败状态更新未恰好影响一行");
                statusException.addSuppressed(exception);
                throw statusException;
            }
        }
        return maskedTaskCopy(findRequiredTask(normalizedTaskNo));
    }

    private void sendConfirmedReplay(String exchange, String routingKey, Message message) {
        CorrelationData correlationData = new CorrelationData();
        rabbitTemplate.send(exchange, routingKey, message, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(publisherConfirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null || !confirm.isAck() || correlationData.getReturned() != null) {
                throw new IllegalStateException("死信重放未被目标队列确认");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("死信重放确认中断", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("死信重放确认失败", exception);
        }
    }

    private Message buildReplayMessage(String payloadSnapshot,
                                       String taskNo,
                                       String messageId,
                                       String correlationId,
                                       ReplayRoute route,
                                       String signatureSecret) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(messageId);
        properties.setCorrelationId(correlationId);
        properties.setHeader("compensationTaskNo", taskNo);
        properties.setHeader("compensationReplay", true);
        byte[] body = payloadSnapshot.getBytes(StandardCharsets.UTF_8);
        properties.setHeader(signatureHeader(route), PaymentEventAmqpSignature.sign(body, signatureSecret));
        return new Message(body, properties);
    }

    private boolean isSourceSignatureVerified(Message message, ReplayRoute route) {
        byte[] body = message.getBody();
        if (!Arrays.equals(new String(body, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8), body)) {
            return false;
        }
        return PaymentEventAmqpSignature.matches(body,
                message.getMessageProperties().getHeaders().get(signatureHeader(route)), signatureSecret(route));
    }

    private String signatureSecret(ReplayRoute route) {
        return "payment.trade.replay".equals(route.replayExchange)
                ? paymentSuccessAmqpSecret : clearingGeneratedAmqpSecret;
    }

    private String signatureHeader(ReplayRoute route) {
        return "payment.trade.replay".equals(route.replayExchange)
                ? PaymentEventAmqpSignature.HEADER_NAME : CLEARING_SIGNATURE_HEADER;
    }

    private PaymentDeadLetterTaskEntity findRequiredTask(String taskNo) {
        PaymentDeadLetterTaskEntity task = paymentDeadLetterTaskMapper.findByTaskNo(normalizeTaskNo(taskNo));
        if (task == null) {
            throw new BusinessException(ErrorCode.PAYMENT_DEAD_LETTER_TASK_NOT_FOUND, "死信任务不存在");
        }
        return task;
    }

    private PaymentDeadLetterTaskQueryDTO normalizeQuery(PaymentDeadLetterTaskQueryDTO query) {
        PaymentDeadLetterTaskQueryDTO normalizedQuery = query == null ? new PaymentDeadLetterTaskQueryDTO() : query;
        normalizedQuery.setTaskStatus(normalizeOptionalQueryField(
                normalizedQuery.getTaskStatus(), TASK_STATUS_MAX_LENGTH, "任务状态"));
        normalizedQuery.setTargetSystem(normalizeOptionalQueryField(
                normalizedQuery.getTargetSystem(), TARGET_SYSTEM_MAX_LENGTH, "目标系统"));
        normalizedQuery.setMessageId(normalizeOptionalQueryField(
                normalizedQuery.getMessageId(), MESSAGE_ID_MAX_LENGTH, "消息ID"));
        normalizedQuery.setPageNo(Math.max(normalizedQuery.getPageNo(), 1));
        normalizedQuery.setPageSize(Math.min(Math.max(normalizedQuery.getPageSize(), 1), 100));
        return normalizedQuery;
    }

    private PaymentDeadLetterTaskActionRequestDTO requireAction(PaymentDeadLetterTaskActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getOperator()) || !StringUtils.hasText(request.getResolutionNote())) {
            throw new IllegalArgumentException("处置人和处置说明不能为空");
        }
        request.setOperator(normalizeActionField(
                request.getOperator(), OPERATOR_MAX_LENGTH, "处置人"));
        request.setResolutionNote(normalizeActionField(
                request.getResolutionNote(), RESOLUTION_NOTE_MAX_LENGTH, "处置说明"));
        return request;
    }

    private String normalizeActionField(String value, int maxLength, String fieldName) {
        if (containsIsoControl(value)) {
            throw new IllegalArgumentException(fieldName + "不能包含控制字符");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (StringUtils.hasText(messageId)) {
            return normalizeRequiredMessageMetadata(messageId, MESSAGE_ID_MAX_LENGTH);
        }
        return "ANON-" + UUID.nameUUIDFromBytes(message.getBody()).toString();
    }

    private String normalizeDeadLetterRoutingKey(String routingKey) {
        if (!StringUtils.hasText(routingKey)
                || containsIsoControl(routingKey)
                || routingKey.length() > ROUTING_KEY_MAX_LENGTH) {
            throw new PaymentDeadLetterTaskService.UnsupportedDeadLetterRouteException();
        }
        return routingKey;
    }

    private String normalizeRequiredMessageMetadata(String value, int maxLength) {
        String normalized = normalizeOptionalMessageMetadata(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw new PaymentDeadLetterTaskService.InvalidDeadLetterMessageException();
        }
        return normalized;
    }

    private String normalizeOptionalMessageMetadata(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (containsIsoControl(value)) {
            throw new PaymentDeadLetterTaskService.InvalidDeadLetterMessageException();
        }
        if (value.length() > maxLength) {
            throw new PaymentDeadLetterTaskService.InvalidDeadLetterMessageException();
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new PaymentDeadLetterTaskService.InvalidDeadLetterMessageException();
        }
        return normalized;
    }

    private String normalizeReplayRequired(String value, int maxLength) {
        String normalized = normalizeReplayOptional(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("死信重放元数据非法");
        }
        return normalized;
    }

    private String normalizeReplayOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (containsIsoControl(value)) {
            throw new IllegalArgumentException("死信重放元数据非法");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException("死信重放元数据非法");
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("死信重放元数据非法");
        }
        return normalized;
    }

    private String writeHeaders(Map<String, Object> headers) {
        try {
            return objectMapper.writeValueAsString(headers == null ? new LinkedHashMap<String, Object>() : headers);
        } catch (JsonProcessingException exception) {
            return String.valueOf(headers);
        }
    }

    private String normalizeTaskNo(String taskNo) {
        if (taskNo != null && containsIsoControl(taskNo)) {
            throw new IllegalArgumentException("死信任务号不能包含控制字符");
        }
        if (!StringUtils.hasText(taskNo)) {
            throw new IllegalArgumentException("死信任务号不能为空");
        }
        if (taskNo.length() > TASK_NO_MAX_LENGTH) {
            throw new IllegalArgumentException("死信任务号不能超过64个字符");
        }
        String normalized = taskNo.trim();
        if (normalized.length() > TASK_NO_MAX_LENGTH) {
            throw new IllegalArgumentException("死信任务号不能超过64个字符");
        }
        return normalized;
    }

    private boolean containsIsoControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeOptionalQueryField(String value, int maxLength, String fieldName) {
        if (value != null && containsIsoControl(value)) {
            throw new IllegalArgumentException(fieldName + "不能包含控制字符");
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String buildFailureNote() {
        return "重放发送失败：[REDACTED]";
    }

    private long normalizePublisherConfirmTimeoutMs(long timeoutMs) {
        if (timeoutMs <= 0) {
            return DEFAULT_CONFIRM_TIMEOUT_MS;
        }
        return Math.min(timeoutMs, MAX_CONFIRM_TIMEOUT_MS);
    }

    private List<PaymentDeadLetterTaskEntity> maskTasks(List<PaymentDeadLetterTaskEntity> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return Collections.emptyList();
        }
        return tasks.stream()
                .filter(task -> task != null)
                .map(this::maskedTaskCopy)
                .collect(java.util.stream.Collectors.toList());
    }

    private PaymentDeadLetterTaskEntity maskedTaskCopy(PaymentDeadLetterTaskEntity task) {
        if (task == null) {
            return null;
        }
        PaymentDeadLetterTaskEntity outwardTask = new PaymentDeadLetterTaskEntity();
        BeanUtils.copyProperties(task, outwardTask);
        outwardTask.setPayloadSnapshot(sensitiveDataMasker.maskPayload(task.getPayloadSnapshot()));
        outwardTask.setHeaderSnapshot(sensitiveDataMasker.maskPayload(task.getHeaderSnapshot()));
        return outwardTask;
    }

    private static final class ReplayRoute {
        private final String targetSystem;
        private final String replayExchange;
        private final String replayRoutingKey;

        private ReplayRoute(String targetSystem, String replayExchange, String replayRoutingKey) {
            this.targetSystem = targetSystem;
            this.replayExchange = replayExchange;
            this.replayRoutingKey = replayRoutingKey;
        }

        private static ReplayRoute fromDeadLetterRoutingKey(String routingKey) {
            Matcher paymentMatcher = PAYMENT_DLQ_PATTERN.matcher(routingKey);
            if (paymentMatcher.matches()) {
                String consumerName = paymentMatcher.group(1);
                String suffix = paymentMatcher.group(2);
                return new ReplayRoute(
                        toTargetSystem(consumerName),
                        "payment.trade.replay",
                        suffix == null
                                ? "payment.success." + consumerName + ".replay.v1"
                                : "payment.success." + consumerName + ".replay." + suffix + ".v1");
            }
            Matcher clearingMatcher = CLEARING_DLQ_PATTERN.matcher(routingKey);
            if (clearingMatcher.matches()) {
                String consumerName = clearingMatcher.group(1);
                String suffix = clearingMatcher.group(2);
                return new ReplayRoute(
                        toTargetSystem(consumerName),
                        "clearing.trade.replay",
                        suffix == null
                                ? "clearing.generated." + consumerName + ".replay.v1"
                                : "clearing.generated." + consumerName + ".replay." + suffix + ".v1");
            }
            return null;
        }

        private static String toTargetSystem(String consumerName) {
            return consumerName + "-system";
        }
    }
}
