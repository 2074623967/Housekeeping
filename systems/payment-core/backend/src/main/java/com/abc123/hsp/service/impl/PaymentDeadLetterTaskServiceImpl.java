package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskActionRequestDTO;
import com.abc123.hsp.dto.PaymentDeadLetterTaskQueryDTO;
import com.abc123.hsp.entity.PaymentDeadLetterTaskEntity;
import com.abc123.hsp.mapper.PaymentDeadLetterTaskMapper;
import com.abc123.hsp.service.PaymentDeadLetterTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private static final int DEFAULT_CONFIRM_TIMEOUT_MS = 5000;

    private final PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final long publisherConfirmTimeoutMs;

    public PaymentDeadLetterTaskServiceImpl(PaymentDeadLetterTaskMapper paymentDeadLetterTaskMapper,
                                            RabbitTemplate rabbitTemplate,
                                            ObjectMapper objectMapper,
                                            @Value("${payment.amqp.publisher-confirm-timeout-ms:5000}")
                                            long publisherConfirmTimeoutMs) {
        this.paymentDeadLetterTaskMapper = paymentDeadLetterTaskMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.publisherConfirmTimeoutMs = publisherConfirmTimeoutMs <= 0
                ? DEFAULT_CONFIRM_TIMEOUT_MS : publisherConfirmTimeoutMs;
    }

    @Override
    public void intake(Message message, String deadLetterRoutingKey) {
        ReplayRoute replayRoute = ReplayRoute.fromDeadLetterRoutingKey(deadLetterRoutingKey);
        if (replayRoute == null) {
            throw new IllegalArgumentException("不支持的死信路由: " + deadLetterRoutingKey);
        }
        MessageProperties properties = message.getMessageProperties();
        PaymentDeadLetterTaskEntity entity = new PaymentDeadLetterTaskEntity();
        entity.setTaskNo("DLQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        entity.setMessageId(resolveMessageId(message));
        entity.setCorrelationId(properties.getCorrelationId());
        entity.setDeadLetterRoutingKey(deadLetterRoutingKey);
        entity.setTargetSystem(replayRoute.targetSystem);
        entity.setReplayExchange(replayRoute.replayExchange);
        entity.setReplayRoutingKey(replayRoute.replayRoutingKey);
        entity.setTaskStatus(STATUS_PENDING_REVIEW);
        entity.setTaskStatusType("warn");
        entity.setPayloadSnapshot(new String(message.getBody(), StandardCharsets.UTF_8));
        entity.setHeaderSnapshot(writeHeaders(properties.getHeaders()));
        entity.setReplayCount(0);
        paymentDeadLetterTaskMapper.insertIgnore(entity);
    }

    @Override
    public PageResultDTO<PaymentDeadLetterTaskEntity> list(PaymentDeadLetterTaskQueryDTO query) {
        PaymentDeadLetterTaskQueryDTO normalizedQuery = normalizeQuery(query);
        return new PageResultDTO<PaymentDeadLetterTaskEntity>(
                paymentDeadLetterTaskMapper.findAll(normalizedQuery),
                paymentDeadLetterTaskMapper.count(normalizedQuery),
                normalizedQuery.getPageNo(),
                normalizedQuery.getPageSize());
    }

    @Override
    public PaymentDeadLetterTaskEntity markReadyToReplay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        if (paymentDeadLetterTaskMapper.markReadyToReplay(normalizeTaskNo(taskNo), normalizedRequest.getOperator(),
                normalizedRequest.getResolutionNote()) <= 0) {
            throw new IllegalArgumentException("死信任务不存在或当前状态不允许重放");
        }
        return findRequiredTask(taskNo);
    }

    @Override
    public PaymentDeadLetterTaskEntity markManuallyResolved(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        if (paymentDeadLetterTaskMapper.markManuallyResolved(normalizeTaskNo(taskNo), normalizedRequest.getOperator(),
                normalizedRequest.getResolutionNote()) <= 0) {
            throw new IllegalArgumentException("死信任务不存在或当前状态不允许人工结案");
        }
        return findRequiredTask(taskNo);
    }

    @Override
    public PaymentDeadLetterTaskEntity replay(String taskNo, PaymentDeadLetterTaskActionRequestDTO request) {
        PaymentDeadLetterTaskActionRequestDTO normalizedRequest = requireAction(request);
        String normalizedTaskNo = normalizeTaskNo(taskNo);
        if (paymentDeadLetterTaskMapper.markReplaying(normalizedTaskNo, normalizedRequest.getOperator()) <= 0) {
            throw new IllegalArgumentException("死信任务尚未完成复核或已被其他操作处理");
        }
        PaymentDeadLetterTaskEntity task = findRequiredTask(normalizedTaskNo);
        try {
            rabbitTemplate.invoke(operations -> {
                operations.send(task.getReplayExchange(), task.getReplayRoutingKey(), buildReplayMessage(task));
                operations.waitForConfirmsOrDie(publisherConfirmTimeoutMs);
                return null;
            });
            paymentDeadLetterTaskMapper.markReplayed(normalizedTaskNo);
        } catch (RuntimeException exception) {
            paymentDeadLetterTaskMapper.markReplayFailed(normalizedTaskNo, buildFailureNote(exception));
        }
        return findRequiredTask(normalizedTaskNo);
    }

    private Message buildReplayMessage(PaymentDeadLetterTaskEntity task) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(task.getMessageId());
        properties.setCorrelationId(task.getCorrelationId());
        properties.setHeader("compensationTaskNo", task.getTaskNo());
        properties.setHeader("compensationReplay", true);
        return new Message(task.getPayloadSnapshot().getBytes(StandardCharsets.UTF_8), properties);
    }

    private PaymentDeadLetterTaskEntity findRequiredTask(String taskNo) {
        PaymentDeadLetterTaskEntity task = paymentDeadLetterTaskMapper.findByTaskNo(normalizeTaskNo(taskNo));
        if (task == null) {
            throw new IllegalArgumentException("死信任务不存在");
        }
        return task;
    }

    private PaymentDeadLetterTaskQueryDTO normalizeQuery(PaymentDeadLetterTaskQueryDTO query) {
        PaymentDeadLetterTaskQueryDTO normalizedQuery = query == null ? new PaymentDeadLetterTaskQueryDTO() : query;
        normalizedQuery.setTaskStatus(trimToNull(normalizedQuery.getTaskStatus()));
        normalizedQuery.setTargetSystem(trimToNull(normalizedQuery.getTargetSystem()));
        normalizedQuery.setMessageId(trimToNull(normalizedQuery.getMessageId()));
        normalizedQuery.setPageNo(Math.max(normalizedQuery.getPageNo(), 1));
        normalizedQuery.setPageSize(Math.min(Math.max(normalizedQuery.getPageSize(), 1), 100));
        return normalizedQuery;
    }

    private PaymentDeadLetterTaskActionRequestDTO requireAction(PaymentDeadLetterTaskActionRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getOperator()) || !StringUtils.hasText(request.getResolutionNote())) {
            throw new IllegalArgumentException("处置人和处置说明不能为空");
        }
        request.setOperator(request.getOperator().trim());
        request.setResolutionNote(request.getResolutionNote().trim());
        return request;
    }

    private String resolveMessageId(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (StringUtils.hasText(messageId)) {
            return messageId.trim();
        }
        return "ANON-" + UUID.nameUUIDFromBytes(message.getBody()).toString();
    }

    private String writeHeaders(Map<String, Object> headers) {
        try {
            return objectMapper.writeValueAsString(headers == null ? new LinkedHashMap<String, Object>() : headers);
        } catch (JsonProcessingException exception) {
            return String.valueOf(headers);
        }
    }

    private String normalizeTaskNo(String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            throw new IllegalArgumentException("死信任务号不能为空");
        }
        return taskNo.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String buildFailureNote(RuntimeException exception) {
        String message = exception.getMessage();
        String normalizedMessage = StringUtils.hasText(message) ? message.trim() : exception.getClass().getSimpleName();
        return "重放发送失败：" + normalizedMessage.substring(0, Math.min(normalizedMessage.length(), 450));
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
            if ("payment.success.clearing.dlq.v1".equals(routingKey)) {
                return new ReplayRoute("clearing-system", "payment.trade.replay", "payment.success.clearing.replay.v1");
            }
            if ("payment.success.accounting.dlq.v1".equals(routingKey)) {
                return new ReplayRoute("accounting-system", "payment.trade.replay", "payment.success.accounting.replay.v1");
            }
            if ("clearing.generated.settlement.dlq.v1".equals(routingKey)) {
                return new ReplayRoute("settlement-system", "clearing.trade.replay", "clearing.generated.settlement.replay.v1");
            }
            if ("clearing.generated.accounting.dlq.v1".equals(routingKey)) {
                return new ReplayRoute("accounting-system", "clearing.trade.replay", "clearing.generated.accounting.replay.v1");
            }
            return null;
        }
    }
}
