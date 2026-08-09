package com.abc123.riskcontrol.entity;

import lombok.Data;

/**
 * 限额规则实体。
 */
@Data
public class LimitRuleEntity {

    /** 主键。 */
    private Long id;
    /** 规则编码。 */
    private String ruleCode;
    /** 规则名称。 */
    private String ruleName;
    /** 适用对象。 */
    private String targetType;
    /** 场景编码。 */
    private String sceneCode;
    /** 限额值。 */
    private String limitValue;
    /** 时间窗。 */
    private String timeWindow;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}

