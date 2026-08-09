package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付提交前的风控准入结果。
 */
@Data
public class PaymentRiskDecisionResultDTO {

    /** 决策结果：PASS/REVIEW/INTERCEPT/REJECT。 */
    private String decision;
    /** 决策样式。 */
    private String decisionType;
    /** 命中规则编码。 */
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
