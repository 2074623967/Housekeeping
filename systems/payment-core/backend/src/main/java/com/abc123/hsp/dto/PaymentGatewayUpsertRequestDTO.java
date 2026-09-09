package com.abc123.hsp.dto;

import lombok.Data;

/** 支付网关新增或编辑候选。 */
@Data
public class PaymentGatewayUpsertRequestDTO {

    private String gatewayCode;
    private String gatewayName;
    private String accessMode;
    private String channelScope;
    private String environmentScope;
    private String apiBaseUrl;
    private String protocolType;
    private String signAlgorithm;
    private String certificateAlias;
    private String certificateStatus;
    private String releaseStage;
    private String grayStrategy;
    private String callbackWhitelist;
    private String adapterRegistry;
    private Integer timeoutMs;
    private String retryPolicy;
    private Boolean enabled;
    private Integer priority;
    private String changeReason;
}
