package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由执行唯一详情。
 */
@Data
public class PaymentRouteExecutionDetailDTO {

    /** 路由记录号。 */
    private String routeNo;
    /** 支付请求号。 */
    private String requestNo;
    /** 支付单号。 */
    private String paymentOrderId;
    private String successFactIssue;

    /** 跨系统业务链路 ID，按支付单稳定派生为 CHAIN-{paymentOrderId}。 */
    public String getBusinessChainId() {
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }
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
    /** 本次路由消费的运营配置快照版本。 */
    private Long opsConfigSnapshotVersion;
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
