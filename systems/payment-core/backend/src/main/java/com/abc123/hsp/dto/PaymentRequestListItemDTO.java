package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付请求列表项，展示一次支付尝试的请求、响应和路由结果。
 */
@Data
public class PaymentRequestListItemDTO {

    /** 请求编号。 */
    private String requestNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 订单号。 */
    private String orderNo;
    /** 支付方式。 */
    private String paymentMethod;
    /** 渠道编码。 */
    private String channelCode;
    /** 路由结果。 */
    private String routeResult;
    /** 请求状态。 */
    private String requestStatus;
    /** 请求状态样式。 */
    private String requestStatusType;
    /** 请求报文。 */
    private String requestPayload;
    /** 响应报文。 */
    private String responsePayload;
    /** 创建时间。 */
    private String createdAt;
}
