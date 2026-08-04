package com.abc123.riskcontrol.entity;

import lombok.Data;

/**
 * 监控规则实体。
 */
@Data
public class MonitorRuleEntity {

    /** 主键。 */
    private Long id;
    /** 规则编码。 */
    private String monitorCode;
    /** 规则名称。 */
    private String monitorName;
    /** 监控对象。 */
    private String monitorTarget;
    /** 告警阈值。 */
    private String alertThreshold;
    /** 通知策略。 */
    private String notifyPolicy;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}

