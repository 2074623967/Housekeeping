package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由执行结果总览指标。
 */
@Data
public class PaymentRouteExecutionOverviewDTO {

    /** 路由记录总数。 */
    private Long totalRouteCount;
    /** 成功命中数。 */
    private Long successRouteCount;
    /** 需关注命中数。 */
    private Long warnRouteCount;
    /** 命中渠道数。 */
    private Integer distinctChannelCount;
    /** 线下路由数。 */
    private Integer offlineRouteCount;
    /** 微信路由数。 */
    private Integer wechatRouteCount;
    /** 支付宝路由数。 */
    private Integer alipayRouteCount;
    /** 最近一次路由时间。 */
    private String latestRouteAt;
}
