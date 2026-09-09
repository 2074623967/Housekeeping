package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关状态变更复核展示项。 */
@Data
public class PaymentGatewayReviewDTO {

    private String requestNo;
    private String gatewayCode;
    private String gatewayName;
    private String accessMode;
    private String baseFingerprint;
    private String candidateStatus;
    private String candidateStatusType;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
    private String createdAt;
    private String reviewedAt;
    private String identityTrustLevel;
}
