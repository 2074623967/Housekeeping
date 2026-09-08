package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码单条变更复核申请展示项。
 */
@Data
public class PaymentReturnCodeReviewDTO {

    private String requestNo;
    private String channelCode;
    private String channelReturnCode;
    private String standardizedCode;
    private String standardizedMessage;
    private String mappingVersion;
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
    /** 身份可信度固定为自声明，避免被误读成正式审批。 */
    private String identityTrustLevel;
}
