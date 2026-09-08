package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由规则冲突检测结果。
 */
@Data
public class PaymentRouteRuleConflictDTO {

    /** 冲突类型。 */
    private String conflictType;
    /** 严重级别。 */
    private String severity;
    /** 严重级别样式类型。 */
    private String severityType;
    /** 当前规则编码。 */
    private String ruleCode;
    /** 当前规则名称。 */
    private String ruleName;
    /** 对照规则编码；单规则问题时为空。 */
    private String conflictingRuleCode;
    /** 对照规则名称；单规则问题时为空。 */
    private String conflictingRuleName;
    /** 当前规则优先级。 */
    private Integer priority;
    /** 对照规则优先级。 */
    private Integer conflictingPriority;
    /** 两条规则发生交集的场景说明。 */
    private String overlappingScene;
    /** 检测结论。 */
    private String message;
}
