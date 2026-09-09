package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/** 支付渠道状态变更复核申请实体。 */
@Data
public class PaymentChannelReviewEntity {

    private String requestNo;
    private String channelCode;
    private String operationType;
    private String channelName;
    private String paymentMethod;
    private String merchantNo;
    private String merchantAppId;
    private String callbackSecret;
    private String callbackSignAlgorithm;
    private String callbackPublicKey;
    private String callbackNotifyUrl;
    private String certificateProfile;
    private Integer notifySignWindowSec;
    private Integer refundWindowDays;
    private String riskControlTag;
    private String sceneScope;
    private BigDecimal dailyLimit;
    private Integer priority;
    private Integer routeWeight;
    private String pendingChannelCode;
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
