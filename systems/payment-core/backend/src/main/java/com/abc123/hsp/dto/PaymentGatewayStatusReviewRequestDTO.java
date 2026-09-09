package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关启停复核提交请求。 */
@Data
public class PaymentGatewayStatusReviewRequestDTO {

    private String gatewayCode;
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
