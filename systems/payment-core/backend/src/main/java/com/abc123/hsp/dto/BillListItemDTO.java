package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 账单中心列表项，用于后台展示订单对应账单的支付进展。
 */
@Data
public class BillListItemDTO {

    /** 账单号。 */
    private String billNo;
    /** 关联订单号。 */
    private String orderNo;
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
}
