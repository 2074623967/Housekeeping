package com.abc123.hsp.entity;

import lombok.Data;

/** 支付控制策略配置变更复核申请实体。 */
@Data
public class PaymentControlPolicyReviewEntity {

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
    private String accessTokenValue;
    private String strictMode;
    private String pendingSourceAppId;
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
