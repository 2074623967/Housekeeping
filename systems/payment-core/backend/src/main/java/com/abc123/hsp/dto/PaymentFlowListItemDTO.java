package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付流水中心列表项，统一展示支付主链路中的关键过程记录。
 */
@Data
public class PaymentFlowListItemDTO {

    /** 流水编号。 */
    private String flowNo;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联订单号。 */
    private String orderNo;
    /** 关联预付单号。 */
    private String prepayOrderNo;
    /** 流水类型。 */
    private String flowType;
    /** 流水类型样式。 */
    private String flowTypeTag;
    /** 渠道编码。 */
    private String channelCode;
    /** 业务状态。 */
    private String businessStatus;
    /** 状态样式。 */
    private String businessStatusType;
    /** 流水摘要。 */
    private String summary;
    /** 创建时间。 */
    private String createdAt;
}
