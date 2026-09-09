package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略完整候选复核提交请求。 */
@Data
public class PaymentControlPolicyReviewSubmitRequestDTO {

    private String requester;
    private PaymentControlPolicyUpsertRequestDTO candidate;
}
