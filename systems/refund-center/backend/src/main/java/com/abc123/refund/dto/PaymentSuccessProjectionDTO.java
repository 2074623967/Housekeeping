package com.abc123.refund.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * payment-core 投影到退款中心的支付成功事实。
 */
@Data
public class PaymentSuccessProjectionDTO {

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
}

