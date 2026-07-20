package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 证书配置。
 */
@Data
public class GatewayCertificateDTO {

    private String certificateCode;
    private String gatewayCode;
    private String certificateVersion;
    private String expireAt;
    private String status;
    private String statusType;
    private String updatedAt;
}
