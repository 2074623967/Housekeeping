package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略复核处理请求。 */
@Data
public class PaymentControlPolicyReviewActionRequestDTO {

    private String reviewer;
    private String reviewComment;
}
