package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付协议复核申请展示项。
 */
@Data
public class PaymentProtocolReviewDTO {

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
    private String createdAt;
    private String reviewedAt;
    /** 身份可信度固定为自声明，避免被误读成已认证签字。 */
    private String identityTrustLevel;
}
