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
    /** 异常时间。 */
    private String createdAt;
}
