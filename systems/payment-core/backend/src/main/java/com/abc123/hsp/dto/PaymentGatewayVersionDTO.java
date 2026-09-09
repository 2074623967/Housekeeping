package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关不可变版本展示项。 */
@Data
public class PaymentGatewayVersionDTO {

    private String gatewayCode;
    private Integer versionNo;
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
    private String status;
    private String statusType;
    private Integer priority;
    private String changeType;
    private Integer sourceVersionNo;
    private String changeReason;
    private String createdAt;
}
