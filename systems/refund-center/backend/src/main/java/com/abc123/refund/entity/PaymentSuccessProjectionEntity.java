package com.abc123.refund.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 支付成功事实投影实体，对应 t_refund_payment_source。
 */
@Data
public class PaymentSuccessProjectionEntity {

    /** 数据库主键。 */
    private Long id;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 业务订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 已支付金额。 */
    private BigDecimal paidAmount;
    /** 支付渠道。 */
    private String channelCode;
    /** 支付成功时间。 */
    private LocalDateTime paidAt;
}

