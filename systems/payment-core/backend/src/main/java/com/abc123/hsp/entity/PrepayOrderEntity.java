package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 预付单实体，对应表：t_prepay_order。
 */
@Data
public class PrepayOrderEntity {

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 关联账单号。 */
    private String billNo;
    /** 关联订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 预付金额。 */
    private BigDecimal amount;
    /** 支付场景。 */
    private String payScene;
    /** 收银台标题。 */
    private String cashierTitle;
    /** 收银台状态。 */
    private String cashierStatus;
    /** 收银台状态样式类型。 */
    private String cashierStatusType;
    /** 关联支付单号。 */
    private String paymentOrderId;
}
