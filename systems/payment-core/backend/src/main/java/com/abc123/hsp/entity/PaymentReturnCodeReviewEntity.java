package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 渠道返回码单条变更复核申请实体，对应表：t_payment_return_code_review。
 */
@Data
public class PaymentReturnCodeReviewEntity {

    private String requestNo;
    private String channelCode;
    private String channelReturnCode;
    private String standardizedCode;
    private String standardizedMessage;
    private String mappingVersion;
    private String pendingMappingKey;
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
