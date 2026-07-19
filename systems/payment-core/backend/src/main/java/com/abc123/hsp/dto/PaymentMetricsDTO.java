package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付核心实时健康指标。
 */
@Data
public class PaymentMetricsDTO {

    /** 支付单总数。 */
    private long totalCount;
    /** 支付成功单数。 */
    private long successCount;
    /** 支付处理中单数。 */
    private long pendingCount;
    /** 已关闭支付单数。 */
    private long closedCount;
    /** 成功支付金额。 */
    private String successAmount;
    /** 支付成功率。 */
    private String successRate;
}
