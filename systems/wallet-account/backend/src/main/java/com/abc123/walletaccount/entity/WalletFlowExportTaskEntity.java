package com.abc123.walletaccount.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletFlowExportTaskEntity {

    /** 数据库主键。 */
    private Long id;
    /** 导出任务编号。 */
    private String exportTaskNo;
    /** 钱包账户编号筛选条件。 */
    private String walletAccountNo;
    /** 来源系统筛选条件。 */
    private String sourceSystem;
    /** 来源业务单号筛选条件。 */
    private String sourceBizNo;
    /** 操作人编号。 */
    private String operatorId;
    /** 操作人名称。 */
    private String operatorName;
    /** 任务状态。 */
    private String taskStatus;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
