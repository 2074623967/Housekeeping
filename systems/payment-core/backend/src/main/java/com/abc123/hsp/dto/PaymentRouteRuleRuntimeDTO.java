package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 路由执行时使用的规则配置。
 */
@Data
public class PaymentRouteRuleRuntimeDTO {

    /** 规则编码。 */
    private String ruleCode;
    /** 规则名称。 */
    private String ruleName;
    /** 匹配场景。 */
    private String matchScene;
    /** 匹配表达式。 */
    private String matchExpression;
    /** 目标渠道。 */
    private String targetChannelCode;
    /** 兜底渠道。 */
    private String fallbackChannelCode;
    /** 优先级。 */
    private Integer priority;
}
