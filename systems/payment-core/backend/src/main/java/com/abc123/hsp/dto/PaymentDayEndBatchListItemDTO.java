package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付日终批次列表项。
 */
@Data
public class PaymentDayEndBatchListItemDTO {

    /** 批次号。 */
    private String batchNo;
    /** 业务日期。 */
    private String bizDate;
    /** 执行方式。 */
    private String runMode;
    /** 批次状态。 */
    private String batchStatus;
    /** 批次状态样式类型。 */
    private String batchStatusType;
    /** 支付总单量。 */
    private Integer paymentTotalCount;
    /** 支付成功单量。 */
    private Integer paymentSuccessCount;
    /** 支付成功金额。 */
    private String paymentSuccessAmount;
    /** 渠道成功单量。 */
    private Integer channelSuccessCount;
    /** 渠道成功金额。 */
    private String channelSuccessAmount;
    /** 内部事件成功单量。 */
    private Integer internalSuccessCount;
    /** 内部事件成功金额。 */
    private String internalSuccessAmount;
    /** 支付成功差异单量。 */
    private Integer paymentSuccessGapCount;
    /** 支付成功差异金额。 */
    private String paymentSuccessGapAmount;
    /** 退款成功单量。 */
    private Integer refundSuccessCount;
    /** 退款成功金额。 */
    private String refundSuccessAmount;
    /** 渠道异常数。 */
    private Integer channelAbnormalCount;
    /** 内部异常数。 */
    private Integer internalAbnormalCount;
    /** 待收口退款数。 */
    private Integer pendingRefundCount;
    /** 待收口退款金额。 */
    private String pendingRefundAmount;
    /** 对账准入状态。 */
    private String reconciliationReadinessStatus;
    /** 对账准入状态样式。 */
    private String reconciliationReadinessType;
    /** 对账准入说明。 */
    private String reconciliationReadinessSummary;
    /** 执行备注。 */
    private String summaryComment;
    /** 触发人。 */
    private String triggeredBy;
    /** 创建时间。 */
    private String createdAt;
    /** 完成时间。 */
    private String completedAt;
}
