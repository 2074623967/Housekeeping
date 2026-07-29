package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付日终批次实体，对应表：t_payment_day_end_batch。
 */
@Data
public class PaymentDayEndBatchEntity {

    /** 批次号。 */
    private String batchNo;
    /** 业务日期。 */
    private String bizDate;
    /** 运行方式。 */
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
    private BigDecimal paymentSuccessAmount;
    /** 渠道成功单量。 */
    private Integer channelSuccessCount;
    /** 渠道成功金额。 */
    private BigDecimal channelSuccessAmount;
    /** 内部事件成功单量。 */
    private Integer internalSuccessCount;
    /** 内部事件成功金额。 */
    private BigDecimal internalSuccessAmount;
    /** 支付成功差异单量。 */
    private Integer paymentSuccessGapCount;
    /** 支付成功差异金额。 */
    private BigDecimal paymentSuccessGapAmount;
    /** 退款成功单量。 */
    private Integer refundSuccessCount;
    /** 退款成功金额。 */
    private BigDecimal refundSuccessAmount;
    /** 渠道异常数。 */
    private Integer channelAbnormalCount;
    /** 内部异常数。 */
    private Integer internalAbnormalCount;
    /** 待收口退款数。 */
    private Integer pendingRefundCount;
    /** 待收口退款金额。 */
    private BigDecimal pendingRefundAmount;
    /** 执行备注。 */
    private String summaryComment;
    /** 触发人。 */
    private String triggeredBy;
}
