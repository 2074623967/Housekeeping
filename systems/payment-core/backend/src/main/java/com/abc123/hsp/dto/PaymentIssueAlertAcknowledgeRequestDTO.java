package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付异常告警确认回执请求。
 */
@Data
public class PaymentIssueAlertAcknowledgeRequestDTO {

    /** 回执确认人。 */
    private String operator;
}
