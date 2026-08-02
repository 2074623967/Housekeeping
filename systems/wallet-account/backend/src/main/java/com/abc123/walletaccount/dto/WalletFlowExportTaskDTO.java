package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletFlowExportTaskDTO {

    /** 异步导出任务编号。 */
    private String exportTaskNo;
    /** 导出任务状态。 */
    private String taskStatus;
}
