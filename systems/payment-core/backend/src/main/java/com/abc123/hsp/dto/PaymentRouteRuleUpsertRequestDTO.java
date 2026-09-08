package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由规则新增或编辑请求。
 */
@Data
public class PaymentRouteRuleUpsertRequestDTO {

    /** 路由规则编码。 */
    private String ruleCode;
    /** 路由规则名称。 */
    private String ruleName;
    /** 匹配业务场景。 */
    private String matchScene;
    /** 匹配表达式。 */
    private String matchExpression;
    /** 目标渠道编码。 */
    private String targetChannelCode;
    /** 兜底渠道编码，可为空。 */
    private String fallbackChannelCode;
    /** 规则承接流量百分比，取值 0 到 100。 */
    private Integer rolloutPercentage;
    /** 稳定分桶盐值，变更后会重排支付单桶位。 */
    private String rolloutSalt;
    /** 规则优先级。 */
    private Integer priority;
    /** 是否启用。 */
    private Boolean enabled;
    /** 本次变更原因。 */
    private String changeReason;
}
