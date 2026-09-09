package com.abc123.hsp.entity;

import lombok.Data;

/** 支付网关状态变更复核申请实体。 */
@Data
public class PaymentGatewayReviewEntity {

    private String requestNo;
    private String gatewayCode;
    private String gatewayName;
    private String accessMode;
    private String pendingGatewayCode;
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
