package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 网关渠道配置。
 */
@Data
public class GatewayChannelDTO {

    private String gatewayCode;
    private String gatewayName;
    private String channelType;
    private String protocolType;
    private String signAlgorithm;
    private String endpoint;
    private String status;
    private String statusType;
    private String updatedAt;
}
