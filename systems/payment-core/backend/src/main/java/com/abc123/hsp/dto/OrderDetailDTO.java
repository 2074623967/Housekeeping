package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 订单支付联查详情。
 */
@Data
public class OrderDetailDTO {

    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 服务品类。 */
    private String serviceType;
    /** 服务者名称。 */
    private String workerName;
    /** 订单应收金额。 */
    private String orderAmount;
    /** 订单已付金额。 */
    private String paidAmount;
    /** 订单剩余应付金额。 */
    private String outstandingAmount;
    /** 订单状态。 */
    private String orderStatus;
    /** 订单状态样式。 */
    private String orderStatusType;
    /** 履约状态。 */
    private String fulfillmentStatus;
    /** 履约状态样式。 */
    private String fulfillmentStatusType;
    /** 订单创建时间。 */
    private String createdAt;

    /** 最近账单号。 */
    private String billNo;
    /** 最近账单应收金额。 */
    private String billAmount;
    /** 最近账单已付金额。 */
    private String billPaidAmount;
    /** 最近账单状态。 */
    private String billStatus;
    /** 最近账单状态样式。 */
    private String billStatusType;
    /** 最近账单到期时间。 */
    private String billDueAt;

    /** 最近支付单号。 */
    private String latestPaymentOrderId;
    /** 最近支付金额。 */
    private String latestPaymentAmount;
    /** 最近支付方式。 */
    private String latestPaymentMethod;
    /** 最近支付渠道。 */
    private String latestPaymentChannel;
    /** 最近支付状态。 */
    private String latestPaymentStatus;
    /** 最近支付状态样式。 */
    private String latestPaymentStatusType;
    /** 最近支付成功时间。 */
    private String latestPaymentSuccessAt;

    /** 最近预付单号。 */
    private String latestPrepayOrderNo;
    /** 最近预付金额。 */
    private String latestPrepayAmount;
    /** 最近支付场景。 */
    private String latestPayScene;
    /** 最近收银台状态。 */
    private String latestCashierStatus;
    /** 最近收银台状态样式。 */
    private String latestCashierStatusType;
    /** 最近预付单失效时间。 */
    private String latestPrepayExpiresAt;
}
