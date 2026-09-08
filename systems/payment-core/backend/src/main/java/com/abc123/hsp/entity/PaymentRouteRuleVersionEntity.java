package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付路由规则版本快照实体，对应表：t_payment_route_rule_version。
 */
@Data
public class PaymentRouteRuleVersionEntity {

    /** 路由规则编码。 */
    private String ruleCode;
    /** 版本号。 */
    private Integer versionNo;
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
    /** 规则承接流量百分比。 */
    private Integer rolloutPercentage;
    /** 稳定分桶盐值。 */
    private String rolloutSalt;
    /** 规则状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
    /** 规则优先级。 */
    private Integer priority;
    /** 变更类型。 */
    private String changeType;
    /** 回滚来源版本号。 */
    private Integer sourceVersionNo;
    /** 变更原因。 */
    private String changeReason;
}
