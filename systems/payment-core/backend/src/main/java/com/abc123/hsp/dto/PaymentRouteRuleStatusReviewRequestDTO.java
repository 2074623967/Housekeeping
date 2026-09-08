package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 路由规则启停复核申请参数。
 */
@Data
public class PaymentRouteRuleStatusReviewRequestDTO {

    /** 路由规则编码。 */
    private String ruleCode;
    /** 是否启用。 */
    private Boolean enabled;
    /** 申请人声明；当前未接入可信身份源。 */
    private String requester;
    /** 本次启停原因。 */
    private String changeReason;
}
