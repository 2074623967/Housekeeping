package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 账单实体，对应表：t_bill。
 */
@Data
public class BillEntity {

    /** 账单号。 */
    private String billNo;
    /** 关联订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 账单应收金额。 */
    private BigDecimal billAmount;
    /** 账单已支付金额。 */
    private BigDecimal paidAmount;
    /** 账单状态。 */
    private String billStatus;
    /** 账单状态样式类型。 */
    private String billStatusType;
}
