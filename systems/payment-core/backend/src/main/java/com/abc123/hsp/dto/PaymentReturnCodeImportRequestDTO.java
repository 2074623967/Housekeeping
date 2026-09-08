package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道返回码 CSV 预检请求。
 */
@Data
public class PaymentReturnCodeImportRequestDTO {

    /** UTF-8 CSV 原文。 */
    private String csvContent;
}
