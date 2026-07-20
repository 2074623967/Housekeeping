package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由决策结果。
 */
@Data
public class PaymentRouteDecisionDTO {

    /** 实际命中的渠道编码。 */
    private String channelCode;
    /** 路由规则标识。 */
    private String routeRule;
    /** 路由结果描述。 */
    private String routeResult;
}
