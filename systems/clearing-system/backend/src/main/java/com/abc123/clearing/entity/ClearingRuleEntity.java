package com.abc123.clearing.entity;

import lombok.Data;

/**
 * 清分规则实体。
 */
@Data
public class ClearingRuleEntity {

    private String ruleNo;
    private String ruleName;
    private String ruleType;
    private String ruleExpression;
    private String ruleStatus;
    private String versionNo;
    private String greyFlag;
    private String createdAt;
}
