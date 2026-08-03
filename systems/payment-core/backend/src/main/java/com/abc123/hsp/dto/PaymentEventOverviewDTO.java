package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付事件出站总览指标。
 */
@Data
public class PaymentEventOverviewDTO {

    /** 事件总数。 */
    private Long totalEventCount;
    /** 发布成功事件数。 */
    private Long successEventCount;
    /** 发布中事件数。 */
    private Long pendingEventCount;
    /** 发布失败事件数。 */
    private Long failedEventCount;
    /** 死信事件数。 */
    private Long deadLetterEventCount;
    /** 失败或死信事件数。 */
    private Long failedOrDeadLetterCount;
    /** 涉及下游系统数。 */
    private Integer distinctDownstreamCount;
    /** 需要重试的事件数。 */
    private Integer dueRetryEventCount;
    /** 成功支付事件数。 */
    private Integer paymentSuccessEventCount;
    /** 最近发布时间。 */
    private String latestPublishedAt;
}
