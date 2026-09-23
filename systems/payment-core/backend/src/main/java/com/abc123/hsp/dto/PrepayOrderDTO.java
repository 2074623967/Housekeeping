package com.abc123.hsp.dto;

import java.util.Map;
import lombok.Data;

/**
 * 预付单展示模型。
 */
@Data
public class PrepayOrderDTO {

    /**
     * 原生终端支付参数。仅由本次提交响应临时携带，不作为预付单持久化字段。
     */
    private Map<String, String> nativePaymentParams;

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 账单号。 */
    private String billNo;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 待支付金额。 */
    private String amount;
    /** 支付场景。 */
    private String payScene;
    /** 收银台标题。 */
    private String cashierTitle;
    /** 收银台状态。 */
    private String cashierStatus;
    /** 收银台状态样式。 */
    private String cashierStatusType;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 支付状态。 */
    private String paymentStatus;
    /** 创建时间。 */
    private String createdAt;
    /** 过期时间。 */
    private String expiresAt;
}
