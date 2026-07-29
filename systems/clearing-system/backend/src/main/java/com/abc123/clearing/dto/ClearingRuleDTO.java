package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分规则展示对象。
 */
@Data
public class ClearingRuleDTO {

    private String ruleNo;
    private String ruleName;
    private String ruleType;
    private String ruleExpression;
    private String ruleStatus;
    private String ruleStatusType;
    private String versionNo;
    private String greyFlag;
    private String createdAt;
}
