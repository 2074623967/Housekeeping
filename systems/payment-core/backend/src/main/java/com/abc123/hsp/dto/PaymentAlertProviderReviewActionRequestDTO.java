package com.abc123.hsp.dto;

import lombok.Data;

/** 异常告警通知供应商复核处理请求。 */
@Data
public class PaymentAlertProviderReviewActionRequestDTO {

    private String reviewer;
    private String reviewComment;
}
