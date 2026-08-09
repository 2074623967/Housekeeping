package com.abc123.refund.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 退款列表项。
 */
@Data
public class RefundListItemDTO {

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
    /** 申请时间。 */
    private LocalDateTime appliedAt;
    /** 成功时间。 */
    private LocalDateTime successAt;
}

