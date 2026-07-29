package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付交易异常告警通知日志实体。
 */
@Data
public class PaymentIssueAlertLogEntity {

    /** 告警编号。 */
    private String alertNo;
    /** 来源站内告警编号。 */
    private String sourceAlertNo;
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
    /** 命中的供应商编码。 */
    private String providerCode;
    /** 命中的供应商名称。 */
    private String providerName;
    /** 命中的供应商接入端点。 */
    private String endpointAlias;
    /** 命中的消息模板编码。 */
    private String templateCode;
    /** 供应商原始回执快照。 */
    private String providerReceiptSnapshot;
    /** 供应商侧回执号。 */
    private String providerReceiptNo;
    /** 供应商侧投递状态。 */
    private String providerDeliveryStatus;
    /** 供应商侧投递说明。 */
    private String providerDeliveryMessage;
    /** 渲染后的告警内容快照。 */
    private String renderedContentSnapshot;
    /** 回执确认人。 */
    private String ackOperator;
    /** 回执确认时间。 */
    private String ackAt;
    /** 创建时间。 */
    private String createdAt;
    /** 触发来源。 */
    private String triggeredBy;
}
