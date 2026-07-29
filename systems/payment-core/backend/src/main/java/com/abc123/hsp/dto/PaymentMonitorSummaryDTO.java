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
    /** 待回调支付单量。 */
    private Integer pendingPaymentCount;
    /** 失败退款单量。 */
    private Integer failedRefundCount;
    /** 已停用渠道数。 */
    private Integer disabledChannelCount;
    /** 当前异常告警数。 */
    private Integer alertCount;
}
