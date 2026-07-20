package com.abc123.gatewayaccess.entity;

import lombok.Data;

/**
 * 接入应用实体。
 */
@Data
public class GatewayAppEntity {

    /** 主键ID。 */
    private Long id;
    /** 应用编码。 */
    private String appCode;
    /** 应用名称。 */
    private String appName;
    /** 来源系统。 */
    private String sourceSystem;
    /** 负责人。 */
    private String owner;
    /** IP 白名单。 */
    private String ipWhitelist;
    /** 权限范围。 */
    private String permissionScope;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
