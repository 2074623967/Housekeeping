package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 接入应用配置。
 */
@Data
public class GatewayAppDTO {

    private String appCode;
    private String appName;
    private String sourceSystem;
    private String owner;
    private String ipWhitelist;
    private String permissionScope;
    private String status;
    private String statusType;
    private String updatedAt;
}
