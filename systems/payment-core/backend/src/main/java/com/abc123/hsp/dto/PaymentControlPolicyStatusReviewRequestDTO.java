package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略启停候选提交请求。 */
@Data
public class PaymentControlPolicyStatusReviewRequestDTO {

    private String sourceAppId;
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
