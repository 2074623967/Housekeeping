package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 创建清分规则请求。
 */
@Data
public class CreateClearingRuleRequestDTO {

    private String ruleName;
    private String ruleType;
    private String ruleExpression;
    private String greyFlag;
}
