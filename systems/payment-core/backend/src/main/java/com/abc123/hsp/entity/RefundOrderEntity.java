package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款单实体，对应表：t_refund_order。
 */
@Data
public class RefundOrderEntity {

    /** 退款单号。 */
    private String refundOrderId;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式。 */
    private String refundMethod;
    /** 退款状态。 */
    private String status;
    /** 退款状态样式类型。 */
    private String statusType;
    /** 申请时间。 */
    private String appliedAt;
    /** 退款成功时间。 */
    private String successAt;
}
