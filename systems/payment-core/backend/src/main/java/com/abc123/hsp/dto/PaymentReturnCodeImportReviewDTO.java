package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码导入复核申请展示项。
 */
@Data
public class PaymentReturnCodeImportReviewDTO {

    private String requestNo;
    private String sourceSha256;
    private String baselineFingerprint;
    private Integer totalCount;
    private Integer createCount;
    private Integer updateCount;
    private Integer unchangedCount;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
    private String appliedBatchNo;
    private String createdAt;
    private String reviewedAt;
    /** 身份可信度固定为自声明，避免被误读成正式审批。 */
    private String identityTrustLevel;
}
