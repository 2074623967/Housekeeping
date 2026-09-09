package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关复核处理请求。 */
@Data
public class PaymentGatewayReviewActionRequestDTO {

    private String reviewer;
    private String reviewComment;
}
