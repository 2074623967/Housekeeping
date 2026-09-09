package com.abc123.hsp.entity;

import lombok.Data;

/** 支付渠道状态变更复核申请实体。 */
@Data
public class PaymentChannelReviewEntity {

    private String requestNo;
    private String channelCode;
    private String channelName;
    private String paymentMethod;
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
