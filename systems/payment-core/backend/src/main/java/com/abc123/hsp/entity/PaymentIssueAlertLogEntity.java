package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付交易异常告警通知日志实体。
 */
@Data
public class PaymentIssueAlertLogEntity {

    /** 告警编号。 */
    private String alertNo;
    /** 异常编号。 */
    private String issueNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 异常类型。 */
    private String issueType;
    /** 严重等级。 */
    private String severity;
    /** 责任组。 */
    private String responsibilityGroup;
    /** 通知通道。 */
    private String alertChannel;
    /** 接收人。 */
    private String receiver;
    /** 告警状态。 */
    private String alertStatus;
    /** 告警状态样式。 */
    private String alertStatusType;
    /** 回执状态。 */
    private String ackStatus;
    /** 回执状态样式。 */
    private String ackStatusType;
    /** 告警内容。 */
    private String alertContent;
    /** 触发来源。 */
    private String triggeredBy;
}
