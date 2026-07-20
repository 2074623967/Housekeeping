package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 接入权限配置。
 */
@Data
public class GatewayPermissionDTO {

    private String permissionCode;
    private String appCode;
    private String scope;
    private String status;
    private String statusType;
    private String updatedAt;
}
