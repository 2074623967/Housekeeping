package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletAccountStatusChangeRequestDTO {

    /** 目标账户状态。 */
    private String targetStatus;
    /** 操作人编号。 */
    private String operatorId;
    /** 操作人角色。 */
    private String operatorRole;
    /** 操作人名称。 */
    private String operatorName;
    /** 状态变更原因。 */
    private String operationReason;
}
