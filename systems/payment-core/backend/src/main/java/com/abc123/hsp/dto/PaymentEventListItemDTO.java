package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付事件出站列表项。
 */
@Data
public class PaymentEventListItemDTO {

    /** 事件号。 */
    private String eventNo;
    /** 事件类型。 */
    private String eventType;
    /** 关联支付单号。 */
    private String paymentOrderId;
    /** 关联业务单号。 */
    private String bizNo;
    /** 事件主题。 */
    private String eventTopic;
    /** 下游系统。 */
    private String downstreamSystem;
    /** 发布状态。 */
    private String publishStatus;
    /** 发布状态样式。 */
    private String publishStatusType;
    /** 重试次数。 */
    private Integer retryCount;
    /** 最近发布时间。 */
    private String lastPublishedAt;
    /** 下次重试时间。 */
    private String nextRetryAt;
    /** 事件摘要。 */
    private String eventPayload;
    /** 创建时间。 */
    private String createdAt;
}
