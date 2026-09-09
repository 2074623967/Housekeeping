package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略配置变更复核展示项。 */
@Data
public class PaymentControlPolicyReviewDTO {

    private String requestNo;
    private String sourceAppId;
    private Integer baseVersionNo;
    private Integer sourceVersionNo;
    private String operationType;
    private String sourceAppName;
    private String allowedPaymentMethods;
    private String allowedChannelCodes;
    private String allowedMerchantNos;
    private Integer minuteSubmitLimit;
    private Integer interfaceMinuteSubmitLimit;
    private String tokenAuthRequired;
    private Boolean accessTokenConfigured;
    private String strictMode;
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
