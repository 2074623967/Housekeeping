package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码导入复核申请参数。
 */
@Data
public class PaymentReturnCodeImportReviewSubmitRequestDTO {

    /** UTF-8 CSV 原文。 */
    private String csvContent;
    /** 差异预检返回的配置基线指纹。 */
    private String baselineFingerprint;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    /** 本次导入原因。 */
    private String changeReason;
}
