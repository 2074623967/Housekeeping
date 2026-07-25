package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 接入权限配置。
 */
@Data
public class GatewayPermissionDTO {

    /** 权限编码。 */
    private String permissionCode;
    /** 应用编码。 */
    private String appCode;
    /** 权限范围。 */
    private String scope;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
