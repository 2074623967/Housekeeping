package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付监控摘要卡片数据。
 */
@Data
public class PaymentMonitorSummaryDTO {

    /** 支付单总量。 */
    private Integer totalCount;
    /** 成功支付单量。 */
    private Integer successCount;
    /** 成功率。 */
    private String successRate;
    /** 成功金额。 */
    private String successAmount;
    /** 待处理支付单量，包含预付已创建及底层待回调。 */
    private Integer pendingPaymentCount;
    /** 已关闭支付单量。 */
    private Integer closedPaymentCount;
    /** 失败退款单量。 */
    private Integer failedRefundCount;
    /** 处理中退款单量。 */
    private Integer processingRefundCount;
    /** 已停用渠道数。 */
    private Integer disabledChannelCount;
    /** 当前异常告警数。 */
    private Integer alertCount;
}
