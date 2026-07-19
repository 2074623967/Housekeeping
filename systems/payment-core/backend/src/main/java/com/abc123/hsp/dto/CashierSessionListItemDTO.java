package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 收银台会话列表项，展示预付单对应的终端会话和失效状态。
 */
@Data
public class CashierSessionListItemDTO {

    /** 会话编号，当前使用预付单号作为业务会话编号。 */
    private String sessionNo;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联订单号。 */
    private String orderNo;
    /** 关联账单号。 */
    private String billNo;
    /** 客户名称。 */
    private String customerName;
    /** 收银台标题。 */
    private String cashierTitle;
    /** 支付终端场景。 */
    private String terminal;
    /** 收银台金额。 */
    private String amount;
    /** 会话状态。 */
    private String sessionStatus;
    /** 会话状态样式。 */
    private String sessionStatusType;
    /** 创建时间。 */
    private String createdAt;
    /** 失效时间。 */
    private String expiresAt;
}
