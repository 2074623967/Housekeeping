package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 唯一支付请求详情，展示一次支付尝试及其精确关联的路由事实。
 */
@Data
public class PaymentRequestDetailDTO {

    /** 支付请求编号，即支付尝试号。 */
    private String requestNo;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 本次尝试关联的预付单号。 */
    private String prepayOrderNo;
    /** 支付单记录的业务订单号。 */
    private String orderNo;
    /** 真实存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 预付单关联账单号。 */
    private String billNo;
    /** 支付方式。 */
    private String paymentMethod;
    /** 来源应用标识。 */
    private String sourceAppId;
    /** 渠道编码。 */
    private String channelCode;
    /** 本次请求精确关联的路由编号。 */
    private String routeNo;
    /** 本次请求精确关联的路由规则。 */
    private String routeRule;
    /** 本次请求精确关联的路由结果。 */
    private String routeResult;
    /** 路由记录使用的运营配置快照版本。 */
    private Long routeOpsConfigSnapshotVersion;
    /** 发起支付请求的终端。 */
    private String terminal;
    /** 脱敏后的客户端 IP。 */
    private String clientIp;
    /** 脱敏后的幂等键。 */
    private String idempotencyKey;
    /** 支付请求状态。 */
    private String requestStatus;
    /** 支付请求状态样式。 */
    private String requestStatusType;
    /** 支付尝试使用的运营配置快照版本。 */
    private Long opsConfigSnapshotVersion;
    /** 脱敏后的请求报文。 */
    private String requestPayload;
    /** 脱敏后的响应报文。 */
    private String responsePayload;
    /** 请求完成时间。 */
    private String completedAt;
    /** 请求创建时间。 */
    private String createdAt;
}
