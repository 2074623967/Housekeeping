package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 单条异常告警回执回查请求。
 */
@Data
public class PaymentIssueAlertReceiptQueryRequestDTO {

    /** 人工回查操作人。 */
    private String operator;
}
