package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 路由规则复核申请实体，对应表：t_payment_route_rule_review。
 */
@Data
public class PaymentRouteRuleReviewEntity {

    private String requestNo;
    private String ruleCode;
    private Integer baseVersionNo;
    private Integer sourceVersionNo;
    private String operationType;
    private String ruleName;
    private String matchScene;
    private String matchExpression;
    private String targetChannelCode;
    private String fallbackChannelCode;
    private Integer rolloutPercentage;
    private String rolloutSalt;
    private String status;
    private String statusType;
    private Integer priority;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
    private Boolean priorityCollisionDetected;
    private Boolean priorityCollisionAcknowledged;
}
