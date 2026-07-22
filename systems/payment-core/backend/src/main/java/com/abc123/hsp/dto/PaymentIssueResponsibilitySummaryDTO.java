package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常责任组统计。
 */
@Data
public class PaymentIssueResponsibilitySummaryDTO {

    /** 责任组名称。 */
    private String groupName;
    /** 责任组展示样式。 */
    private String groupType;
    /** 当前筛选条件下的异常总数。 */
    private long totalCount;
    /** 当前筛选条件下的 SLA 超时异常数。 */
    private long overdueCount;
    /** P1 异常数。 */
    private long p1Count;
    /** P2 异常数。 */
    private long p2Count;
    /** 建议处理动作。 */
    private String suggestedAction;
}
