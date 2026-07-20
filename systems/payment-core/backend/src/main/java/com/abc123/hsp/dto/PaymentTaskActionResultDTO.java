package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付任务执行结果。
 */
@Data
public class PaymentTaskActionResultDTO {

    /** 任务编码。 */
    private String taskCode;
    /** 任务名称。 */
    private String taskName;
    /** 处理总数。 */
    private Integer processedCount;
    /** 成功数。 */
    private Integer successCount;
    /** 失败数。 */
    private Integer failCount;
    /** 执行摘要。 */
    private String summaryComment;
    /** 执行后的最新总览。 */
    private PaymentTaskCenterOverviewDTO overview;
}
