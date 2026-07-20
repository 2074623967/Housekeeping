package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付任务执行日志列表项。
 */
@Data
public class PaymentTaskRunLogItemDTO {

    /** 日志号。 */
    private String taskLogNo;
    /** 任务编码。 */
    private String taskCode;
    /** 任务名称。 */
    private String taskName;
    /** 运行方式。 */
    private String runMode;
    /** 任务状态。 */
    private String taskStatus;
    /** 任务状态样式。 */
    private String taskStatusType;
    /** 处理总数。 */
    private Integer processedCount;
    /** 成功数。 */
    private Integer successCount;
    /** 失败数。 */
    private Integer failCount;
    /** 执行摘要。 */
    private String summaryComment;
    /** 触发人。 */
    private String triggeredBy;
    /** 开始时间。 */
    private String startedAt;
    /** 完成时间。 */
    private String completedAt;
}
