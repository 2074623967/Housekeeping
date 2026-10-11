package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 收银台会话唯一详情，供管理端完成预付、支付、账单与标准订单联查。
 */
@Data
public class CashierSessionDetailDTO {

    /** 会话编号，当前使用预付单号作为业务会话编号。 */
    private String sessionNo;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 关联支付单号。 */
    private String paymentOrderId;

    /** 跨系统业务链路 ID，按支付单稳定派生为 CHAIN-{paymentOrderId}。 */
    public String getBusinessChainId() {
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }
    /** 预付单记录的业务订单号。 */
    private String orderNo;
    /** 真实存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 关联账单号。 */
    private String billNo;
    /** 客户名称。 */
    private String customerName;
    /** 收银台标题。 */
    private String cashierTitle;
    /** 预付单业务支付场景。 */
    private String payScene;
    /** 最近支付尝试终端，未提交时回退为业务支付场景。 */
    private String terminal;
    /** 会话金额。 */
    private String amount;
    /** 动态投影后的会话状态。 */
    private String sessionStatus;
    /** 动态投影后的状态样式。 */
    private String sessionStatusType;
    /** 创建时间。 */
    private String createdAt;
    /** 失效时间。 */
    private String expiresAt;
}
