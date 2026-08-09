package com.abc123.riskcontrol.dto;

import lombok.Data;

/**
 * 支付准入风控评估结果。
 */
@Data
public class RiskDecisionResultDTO {

    /** 决策结果：PASS/REVIEW/INTERCEPT/REJECT。 */
    private String decision;
    /** 决策样式。 */
    private String decisionType;
    /** 命中策略或规则编码。 */
    private String hitCode;
    /** 风险标签。 */
    private String riskTag;
    /** 复核单号。 */
    private String reviewNo;
    /** 风险事件号。 */
    private String eventNo;
    /** 决策说明。 */
    private String message;
}
