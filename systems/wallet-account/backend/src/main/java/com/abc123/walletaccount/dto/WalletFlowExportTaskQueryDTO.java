package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletFlowExportTaskQueryDTO {

    /** 操作人编号。 */
    private String operatorId;
    /** 任务状态。 */
    private String taskStatus;
    /** 操作人角色。 */
    private String operatorRole;
    /** 页码。 */
    private Integer pageNo = 1;
    /** 每页条数。 */
    private Integer pageSize = 10;
}
