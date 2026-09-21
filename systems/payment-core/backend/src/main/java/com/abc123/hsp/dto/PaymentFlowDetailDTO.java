package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 唯一支付过程流水详情，由来源类型和来源编号共同确定。
 */
@Data
public class PaymentFlowDetailDTO {

    /** 流水来源：PAYMENT_ATTEMPT/CHANNEL_NOTIFY/ROUTE_RECORD/BUSINESS_EVENT。 */
    private String flowSource;
    /** 来源事实编号。 */
    private String flowNo;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联业务订单号。 */
    private String orderNo;
    /** 真实存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 可精确关联的预付单号。 */
    private String prepayOrderNo;
    /** 可精确关联的账单号。 */
    private String billNo;
    /** 可精确关联的支付请求号。 */
    private String paymentRequestNo;
    /** 可精确关联的路由记录号。 */
    private String routeNo;
    /** 路由处理结果。 */
    private String routeResult;
    /** 流水类型展示名称。 */
    private String flowType;
    /** 流水类型样式。 */
    private String flowTypeTag;
    /** 支付方式。 */
    private String paymentMethod;
    /** 来源应用标识。 */
    private String sourceAppId;
    /** 渠道编码。 */
    private String channelCode;
    /** 发起终端。 */
    private String terminal;
    /** 脱敏后的客户端 IP。 */
    private String clientIp;
    /** 脱敏后的幂等键。 */
    private String idempotencyKey;
    /** 业务处理状态。 */
    private String businessStatus;
    /** 业务状态样式。 */
    private String businessStatusType;
    /** 渠道回调类型。 */
    private String notifyType;
    /** 路由规则。 */
    private String routeRule;
    /** 下游系统范围。 */
    private String downstreamSystem;
    /** 事件主题。 */
    private String eventTopic;
    /** 事件发布状态。 */
    private String publishStatus;
    /** 事件重试次数。 */
    private Integer retryCount;
    /** 本条事实使用的运营配置快照版本。 */
    private Long opsConfigSnapshotVersion;
    /** 脱敏后的请求报文或事实载荷。 */
    private String requestPayload;
    /** 脱敏后的响应报文或处理结果。 */
    private String responsePayload;
    /** 流水摘要。 */
    private String summary;
    /** 事实完成时间，仅支付尝试可能存在。 */
    private String completedAt;
    /** 事实创建时间。 */
    private String createdAt;
}
