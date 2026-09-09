package com.abc123.hsp.entity;

import lombok.Data;

/** 异常告警通知供应商配置变更复核实体。 */
@Data
public class PaymentAlertProviderReviewEntity {

    private String requestNo;
    private String providerCode;
    private String operationType;
    private String providerName;
    private String channelCode;
    private String endpointAlias;
    private String templateCode;
    private String templateBody;
    private String routeRule;
    private Integer routePriority;
    private String retryPolicy;
    private String rateLimitPolicy;
    private String pendingProviderCode;
    private String baseFingerprint;
    private String candidateStatus;
    private String candidateStatusType;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
}
