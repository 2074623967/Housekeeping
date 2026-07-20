package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由执行结果展示项。
 */
@Data
public class PaymentRouteExecutionListItemDTO {

    /** 路由记录号。 */
    private String routeNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 客户名称。 */
    private String customerName;
    /** 支付金额。 */
    private String amount;
    /** 支付方式。 */
    private String paymentMethod;
    /** 命中渠道编码。 */
    private String channelCode;
    /** 路由规则。 */
    private String routeRule;
    /** 路由结果。 */
    private String routeResult;
    /** 路由结果样式。 */
    private String routeResultType;
    /** 发起终端。 */
    private String terminal;
    /** 客户端 IP。 */
    private String clientIp;
    /** 幂等键。 */
    private String idempotencyKey;
    /** 请求报文。 */
    private String requestPayload;
    /** 响应报文。 */
    private String responsePayload;
    /** 创建时间。 */
    private String createdAt;
}
