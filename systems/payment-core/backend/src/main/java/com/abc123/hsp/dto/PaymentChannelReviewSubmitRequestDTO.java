package com.abc123.hsp.dto;

import lombok.Data;

/** 支付渠道完整候选复核提交请求。 */
@Data
public class PaymentChannelReviewSubmitRequestDTO {

    private PaymentChannelUpsertRequestDTO candidate;
    private String requester;
}
