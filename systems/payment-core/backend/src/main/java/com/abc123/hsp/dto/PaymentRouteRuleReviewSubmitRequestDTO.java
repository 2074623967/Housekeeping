package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 路由规则复核申请提交参数。
 */
@Data
public class PaymentRouteRuleReviewSubmitRequestDTO {

    /** 候选规则快照。 */
    private PaymentRouteRuleUpsertRequestDTO candidate;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
}
