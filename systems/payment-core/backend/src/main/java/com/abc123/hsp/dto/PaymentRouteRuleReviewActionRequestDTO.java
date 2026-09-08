package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 路由规则复核动作参数。
 */
@Data
public class PaymentRouteRuleReviewActionRequestDTO {

    /** 复核人声明；当前未接入可信身份源。 */
    private String reviewer;
    /** 复核意见。 */
    private String reviewComment;
}
