package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常告警派发项。
 */
@Data
public class PaymentIssueAlertDispatchItemDTO {

    /** 原始告警编号。 */
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
    /** 接收人。 */
    private String receiver;
    /** 路由配置的通知通道列表。 */
    private String notifyChannels;
    /** 路由配置的升级等级。 */
    private String escalationLevel;
    /** 路由配置的班次标签。 */
    private String scheduleTag;
    /** 告警内容。 */
    private String alertContent;
    /** 触发来源。 */
    private String triggeredBy;
}
