package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付路由规则配置实体，对应表：t_payment_route_rule_config。
 */
@Data
public class PaymentRouteRuleConfigEntity {

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
    /** 兜底渠道编码。 */
    private String fallbackChannelCode;
    /** 状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
    /** 优先级。 */
    private Integer priority;
}
