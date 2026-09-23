package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付监控指标口径定义。
 */
@Data
public class PaymentMonitorMetricDefinitionDTO {

    /** 指标编码。 */
    private String metricCode;
    /** 指标名称。 */
    private String metricName;
    /** 指标定义。 */
    private String definition;
    /** 展示单位。 */
    private String unit;
    /** 当前默认运营门槛。 */
    private String defaultThreshold;
    /** 告警等级。 */
    private String alertLevel;
    /** 责任方。 */
    private String owner;
    /** 建议下钻入口。 */
    private String actionRoute;
}
