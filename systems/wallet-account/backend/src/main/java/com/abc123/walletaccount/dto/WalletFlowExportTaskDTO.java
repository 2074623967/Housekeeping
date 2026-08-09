package com.abc123.walletaccount.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletFlowExportTaskDTO {

    /** 异步导出任务编号。 */
    private String exportTaskNo;
    /** 导出任务状态。 */
    private String taskStatus;
    /** 钱包账户号筛选条件。 */
    private String walletAccountNo;
    /** 来源系统筛选条件。 */
    private String sourceSystem;
    /** 来源业务单号筛选条件。 */
    private String sourceBizNo;
    /** 操作人编号。 */
    private String operatorId;
    /** 操作人名称。 */
    private String operatorName;
    /** 下载地址。 */
    private String downloadPath;
    /** 归档状态。 */
    private String archiveStatus;
    /** 归档文件名。 */
    private String archiveFileName;
    /** 下载次数。 */
    private Integer downloadCount;
    /** 最后下载时间。 */
    private LocalDateTime lastDownloadedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}
