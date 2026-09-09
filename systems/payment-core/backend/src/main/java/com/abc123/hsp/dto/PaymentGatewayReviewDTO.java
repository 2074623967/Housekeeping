package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关配置变更复核展示项。 */
@Data
public class PaymentGatewayReviewDTO {

    private String requestNo;
    private String gatewayCode;
    private String operationType;
    private String gatewayName;
    private String accessMode;
    private String channelScope;
    private String environmentScope;
    private String apiBaseUrl;
    private String protocolType;
    private String signAlgorithm;
    private String certificateAlias;
    private String certificateStatus;
    private String certificateStatusType;
    private String releaseStage;
    private String grayStrategy;
    private String callbackWhitelist;
    private String adapterRegistry;
    private Integer timeoutMs;
    private String retryPolicy;
    private Integer priority;
    private String baseFingerprint;
    private String candidateStatus;
    private String candidateStatusType;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
    private String createdAt;
    private String reviewedAt;
    private String identityTrustLevel;
}
