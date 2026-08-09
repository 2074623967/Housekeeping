package com.abc123.riskcontrol.dto;

import lombok.Data;

/**
 * 风险策略视图。
 */
@Data
public class RiskPolicyDTO {

    /** 策略编码。 */
    private String policyCode;
    /** 策略名称。 */
    private String policyName;
    /** 风险维度。 */
    private String riskDimension;
    /** 命中动作。 */
    private String hitAction;
    /** 风险等级。 */
    private String riskLevel;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}

