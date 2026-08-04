package com.abc123.refund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 退款单实体，对应 t_refund_order。
 */
@Data
public class RefundOrderEntity {

    /** 数据库主键。 */
    private Long id;
    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 业务订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 原支付金额。 */
    private BigDecimal paidAmount;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式。 */
    private String refundMethod;
    /** 退款原因。 */
    private String refundReason;
    /** 退款状态。 */
    private String status;
    /** 渠道退款流水号。 */
    private String channelRefundId;
    /** 渠道失败码。 */
    private String failureCode;
    /** 幂等键。 */
    private String idempotencyKey;
    /** 申请时间。 */
    private LocalDateTime appliedAt;
    /** 审核时间。 */
    private LocalDateTime approvedAt;
    /** 提交渠道时间。 */
    private LocalDateTime submittedAt;
    /** 成功时间。 */
    private LocalDateTime successAt;
    /** 乐观锁版本。 */
    private Integer version;
}

