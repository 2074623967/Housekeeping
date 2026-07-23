package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由记录行模型。
 */
@Data
public class PaymentRouteItemDTO {

    /** 路由流水号。 */
    private String routeNo;
    /** 渠道编码。 */
    private String channelCode;
    /** 命中路由规则。 */
    private String routeRule;
    /** 路由结果。 */
    private String routeResult;
    /** 创建时间。 */
    private String createdAt;
}
