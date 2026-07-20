package com.abc123.gatewayaccess.entity;

import lombok.Data;

/**
 * 接入权限实体。
 */
@Data
public class GatewayPermissionEntity {

    /** 主键ID。 */
    private Long id;
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
