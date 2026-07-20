package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由规则配置展示项。
 */
@Data
public class PaymentRouteRuleConfigDTO {

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
    /** 规则状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
    /** 规则优先级。 */
    private Integer priority;
    /** 更新时间。 */
    private String updatedAt;
}
