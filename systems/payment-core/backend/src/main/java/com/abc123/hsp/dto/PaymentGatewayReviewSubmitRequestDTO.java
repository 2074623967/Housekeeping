package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关完整候选复核提交请求。 */
@Data
public class PaymentGatewayReviewSubmitRequestDTO {

    private String requester;
    private PaymentGatewayUpsertRequestDTO candidate;
}
