package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常中心列表行。
 */
@Data
public class PaymentIssueRowDTO {

    /** 异常编号。 */
    private String issueNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 支付方式。 */
    private String paymentMethod;
    /** 渠道编码。 */
    private String channelCode;
    /** 异常类型。 */
    private String issueType;
    /** 异常类型样式。 */
    private String issueTypeTag;
    /** 严重等级。 */
    private String severity;
    /** 严重等级样式。 */
    private String severityType;
    /** 当前支付状态。 */
    private String paymentStatus;
    /** 当前支付状态样式。 */
    private String paymentStatusType;
    /** 异常摘要。 */
    private String issueSummary;
    /** 根因提示。 */
    private String rootCauseHint;
    /** 建议动作。 */
    private String recommendedAction;
    /** 推荐跳转路由。 */
    private String recommendedRoute;
    /** 处理状态。 */
    private String handlingStatus;
    /** 处理状态样式。 */
    private String handlingStatusType;
    /** 当前处理人。 */
    private String assignee;
    /** 责任组。 */
    private String responsibilityGroup;
    /** 责任组样式。 */
    private String responsibilityGroupType;
    /** 责任组处理说明。 */
    private String responsibilityHint;
    /** 最新告警状态。 */
    private String alertStatus;
    /** 最新告警状态样式。 */
    private String alertStatusType;
    /** 最新告警回执状态。 */
    private String alertAckStatus;
    /** 最新告警回执状态样式。 */
    private String alertAckStatusType;
    /** 最新告警接收人。 */
    private String alertReceiver;
    /** 告警派发通道摘要。 */
    private String alertChannelSummary;
    /** 告警派发结果摘要。 */
    private String alertDeliverySummary;
    /** 最新供应商投递摘要。 */
    private String latestAlertProviderSummary;
    /** 最新供应商回执摘要。 */
    private String latestAlertReceiptSummary;
    /** SLA 状态。 */
    private String slaStatus;
    /** SLA 状态样式。 */
    private String slaStatusType;
    /** SLA 剩余或超时说明。 */
    private String slaTimeLeft;
    /** 升级状态。 */
    private String escalationStatus;
    /** 升级状态样式。 */
    private String escalationStatusType;
    /** 升级建议。 */
    private String escalationSuggestion;
    /** 最近处理动作摘要。 */
    private String latestActionSummary;
    /** 最近处理时间。 */
    private String latestActionAt;
    /** 异常时间。 */
    private String createdAt;
}
