package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包风控事件。 */
@Data
public class WalletRiskEventDTO {
    /** 风控事件号。 */
    private String eventNo;
    /** 业务类型。 */
    private String bizType;
    /** 业务单号。 */
    private String bizNo;
    /** 风险等级。 */
    private String riskLevel;
    /** 处理状态。 */
    private String status;
    /** 风险原因。 */
    private String riskReason;
    /** 处理人。 */
    private String handledBy;
    /** 处理备注。 */
    private String handledRemark;
    /** 创建时间。 */
    private String createdAt;
    /** 处理时间。 */
    private String handledAt;
}
