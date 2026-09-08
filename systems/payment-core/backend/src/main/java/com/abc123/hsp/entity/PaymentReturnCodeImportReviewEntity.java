package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 渠道返回码导入复核申请实体，对应表：t_payment_return_code_import_review。
 */
@Data
public class PaymentReturnCodeImportReviewEntity {

    private String requestNo;
    private String pendingImportKey;
    private String csvContent;
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
}
