package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 账单支付联查详情。
 */
@Data
public class BillDetailDTO {

    /** 账单号。 */
    private String billNo;
    /** 关联业务订单号。 */
    private String orderNo;
    /** 存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 客户名称。 */
    private String customerName;
    /** 账单应收金额。 */
    private String billAmount;
    /** 账单已支付金额。 */
    private String paidAmount;
    /** 账单待支付金额。 */
    private String unpaidAmount;
    /** 账单状态。 */
    private String billStatus;
    /** 账单状态样式类型。 */
    private String billStatusType;
    /** 账单到期时间。 */
    private String dueAt;
    /** 账单创建时间。 */
    private String createdAt;

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

    /** 最近关联支付单号。 */
    private String latestPaymentOrderId;
    /** 最近关联支付单对应的预付单号。 */
    private String latestPaymentPrepayOrderNo;
    /** 最近关联支付金额。 */
    private String latestPaymentAmount;
    /** 最近关联支付方式。 */
    private String latestPaymentMethod;
    /** 最近关联支付渠道。 */
    private String latestPaymentChannel;
    /** 最近关联支付状态。 */
    private String latestPaymentStatus;
    /** 最近关联支付状态样式。 */
    private String latestPaymentStatusType;
    /** 最近关联支付成功时间。 */
    private String latestPaymentSuccessAt;
}
