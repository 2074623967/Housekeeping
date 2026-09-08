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
    /** 是否启用基于主动查单事实的路由健康熔断。 */
    private Boolean routeHealthEnabled;
    /** 渠道健康统计回看小时数。 */
    private Integer routeHealthLookbackHours;
    /** 渠道健康熔断失败笔数阈值。 */
    private Integer routeHealthFailureCountThreshold;
    /** 渠道健康熔断失败率阈值。 */
    private Integer routeHealthFailureRateThresholdPercent;
    /** 应用失败率阈值前要求的最小样本数。 */
    private Integer routeHealthMinimumSampleCount;
}
