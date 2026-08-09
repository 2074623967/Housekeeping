package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 运营配置系统控制项。
 */
@Data
public class PaymentOpsSystemControlDTO {

    /** 控制编码。 */
    private String controlCode;
    /** 控制名称。 */
    private String controlName;
    /** 控制范围。 */
    private String controlScope;
    /** 控制值。 */
    private String controlValue;
    /** 风险级别。 */
    private String riskLevel;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
