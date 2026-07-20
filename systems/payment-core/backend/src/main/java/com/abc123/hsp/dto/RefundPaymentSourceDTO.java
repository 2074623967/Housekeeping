package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款发起时使用的原支付单快照。
 */
@Data
public class RefundPaymentSourceDTO {

    /** 原支付单号。 */
    private String paymentOrderId;
    /** 原订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 原支付金额。 */
    private BigDecimal paidAmount;
    /** 原支付状态。 */
    private String status;
    /** 原支付方式。 */
    private String paymentMethod;
}
