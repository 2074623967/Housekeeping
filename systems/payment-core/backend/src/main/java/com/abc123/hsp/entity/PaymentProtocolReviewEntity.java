package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付协议复核申请实体，对应表：t_payment_protocol_review。
 */
@Data
public class PaymentProtocolReviewEntity {

    private String requestNo;
    private String protocolCode;
    private Integer baseVersionNo;
    private Integer sourceVersionNo;
    private String operationType;
    private String protocolName;
    private String protocolType;
    private String protocolTypeName;
    private String templateCode;
    private String templateName;
    private String templateVersion;
    private String signMode;
    private String signElementSpec;
    private String eSignatureProvider;
    private String sceneScope;
    private String channelScope;
    private String merchantAckRequired;
    private String riskControlTag;
    private String protocolBody;
    private String status;
    private String statusType;
    private Integer priority;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
}
