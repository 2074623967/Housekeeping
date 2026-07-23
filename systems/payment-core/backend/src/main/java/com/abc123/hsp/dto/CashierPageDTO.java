package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 收银台页面展示模型。
 */
@Data
public class CashierPageDTO {

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 订单号。 */
    private String orderNo;
    /** 账单号。 */
    private String billNo;
    /** 客户名称。 */
    private String customerName;
    /** 待支付金额。 */
    private String amount;
    /** 支付场景。 */
    private String payScene;
    /** 收银台标题。 */
    private String title;
    /** 收银台状态。 */
    private String status;
    /** 收银台状态样式。 */
    private String statusType;
    /** 过期时间。 */
    private String expiresAt;
    /** 可选支付渠道列表。 */
    private List<String> channels;
}
