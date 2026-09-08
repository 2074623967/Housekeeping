package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码 CSV 预检与应用请求。
 */
@Data
public class PaymentReturnCodeImportRequestDTO {

    /** UTF-8 CSV 原文。 */
    private String csvContent;
    /** 预检返回的配置基线指纹，应用时必填。 */
    private String baselineFingerprint;
    /** 操作人自声明，仅用于审计留痕。 */
    private String operator;
    /** 本次批量导入原因。 */
    private String changeReason;
}
