package com.abc123.hsp.entity;

import lombok.Data;

/**
 * MQ 死信补偿任务实体，对应表：t_payment_dead_letter_task。
 */
@Data
public class PaymentDeadLetterTaskEntity {

    private String taskNo;
    private String messageId;
    private String correlationId;
    private String deadLetterRoutingKey;
    private String targetSystem;
    private String replayExchange;
    private String replayRoutingKey;
    private String taskStatus;
    private String taskStatusType;
    private String resolutionNote;
    private String payloadSnapshot;
    private String headerSnapshot;
    private Integer replayCount;
    private String lastReplayAt;
    private String operator;
    private String resolvedAt;
    private String createdAt;
    private String updatedAt;
}
