package com.abc123.hsp.dto;

import lombok.Data;

/** 支付渠道复核处理请求。 */
@Data
public class PaymentChannelReviewActionRequestDTO {

    private String reviewer;
    private String reviewComment;
}
