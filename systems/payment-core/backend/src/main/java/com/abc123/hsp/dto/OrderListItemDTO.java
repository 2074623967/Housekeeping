package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 订单列表行模型。
 */
@Data
public class OrderListItemDTO {

    /** 订单号。 */
    private String orderNo;
    /** 账单号。 */
    private String billNo;
    /** 客户名称。 */
    private String customerName;
    /** 服务类型。 */
    private String serviceType;
    /** 服务者名称。 */
    private String workerName;
    /** 订单金额。 */
    private String orderAmount;
    /** 已支付金额。 */
    private String paidAmount;
    /** 订单状态。 */
    private String orderStatus;
    /** 订单状态样式。 */
    private String orderStatusType;
    /** 履约状态。 */
    private String fulfillmentStatus;
    /** 履约状态样式。 */
    private String fulfillmentStatusType;
    /** 最新支付单号。 */
    private String latestPaymentOrderId;
    /** 最新支付状态。 */
    private String latestPaymentStatus;
    /** 最新支付状态样式。 */
    private String latestPaymentStatusType;
    /** 最新预付单号。 */
    private String latestPrepayOrderNo;
    /** 最新收银台状态。 */
    private String latestCashierStatus;
    /** 最新收银台状态样式。 */
    private String latestCashierStatusType;
    /** 创建时间。 */
    private String createdAt;
}
