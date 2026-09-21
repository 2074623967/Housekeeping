package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付事件出站唯一详情。
 */
@Data
public class PaymentEventDetailDTO {

    /** 事件号。 */
    private String eventNo;
    /** 事件类型。 */
    private String eventType;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联业务单号。 */
    private String bizNo;
    /** 真实存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 事件主题。 */
    private String eventTopic;
    /** 下游系统。 */
    private String downstreamSystem;
    /** 发布状态。 */
    private String publishStatus;
    /** 发布状态样式。 */
    private String publishStatusType;
    /** 支付成功事件的 HTTP 下游投递阶段。 */
    private String paymentSuccessHttpStage;
    /** 重试次数。 */
    private Integer retryCount;
    /** 最近发布时间。 */
    private String lastPublishedAt;
    /** 下次重试时间。 */
    private String nextRetryAt;
    /** 最近一次成功重发操作人。 */
    private String lastRepublishOperator;
    /** 最近一次成功重发时间。 */
    private String lastRepublishedAt;
    /** 事件载荷。 */
    private String eventPayload;
    /** 创建时间。 */
    private String createdAt;
}
