package com.abc123.hsp.dto;

import lombok.Data;

/** 支付渠道状态变更复核展示项。 */
@Data
public class PaymentChannelReviewDTO {

    private String requestNo;
    private String channelCode;
    private Integer baseVersionNo;
    private Integer sourceVersionNo;
    private String operationType;
    private String channelName;
    private String paymentMethod;
    private String merchantNo;
    private String merchantAppId;
    private String callbackSecretMasked;
    private String callbackSignAlgorithm;
    private Boolean callbackPublicKeyConfigured;
    private String callbackNotifyUrl;
    private String certificateProfile;
    private Integer notifySignWindowSec;
    private Integer refundWindowDays;
    private String riskControlTag;
    private String sceneScope;
    private String dailyLimit;
    private Integer priority;
    private Integer routeWeight;
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
