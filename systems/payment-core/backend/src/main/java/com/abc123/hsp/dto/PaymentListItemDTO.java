package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付单列表行模型。
 */
@Data
public class PaymentListItemDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 支付金额。 */
    private String amount;
    /** 支付方式。 */
    private String paymentMethod;
    /** 支付渠道。 */
    private String channel;
    /** 渠道交易号。 */
    private String channelTransactionNo;
    /** 支付状态。 */
    private String status;
    /** 支付状态样式。 */
    private String statusType;
    /** 创建时间。 */
    private String createdAt;
}
