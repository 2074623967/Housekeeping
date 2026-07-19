package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付单实体，对应表：t_payment_order。
 */
@Data
public class PaymentOrderEntity {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 关联订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 本次支付金额。 */
    private BigDecimal amount;
    /** 支付方式。 */
    private String paymentMethod;
    /** 支付渠道编码。 */
    private String channelCode;
    /** 渠道侧交易号。 */
    private String channelTransactionNo;
    /** 支付状态。 */
    private String status;
    /** 支付状态样式类型。 */
    private String statusType;
}
