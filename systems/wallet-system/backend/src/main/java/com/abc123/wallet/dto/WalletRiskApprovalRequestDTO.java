package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包风控审批请求。 */
@Data
public class WalletRiskApprovalRequestDTO {
    /** 风控事件号。 */
    private String eventNo;
    /** 审批动作。 */
    private String action;
    /** 审批人。 */
    private String handledBy;
    /** 审批备注。 */
    private String handledRemark;
}
