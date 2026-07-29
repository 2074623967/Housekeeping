package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款列表行模型，供退款查询页展示退款单核心摘要信息。
 */
@Data
public class RefundListItemDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 原业务订单号。 */
    private String orderNo;
    /** 申请退款客户名称。 */
    private String customerName;
    /** 本次退款金额。 */
    private String refundAmount;
    /** 本次退款方式。 */
    private String refundMethod;
    /** 当前退款状态。 */
    private String status;
    /** 当前退款状态对应的前端展示样式。 */
    private String statusType;
    /** 退款申请时间。 */
    private String appliedAt;
    /** 退款成功时间。 */
    private String successAt;
}
