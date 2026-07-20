package com.abc123.gatewayaccess.entity;

import lombok.Data;

/**
 * 网关证书实体。
 */
@Data
public class GatewayCertificateEntity {

    /** 主键ID。 */
    private Long id;
    /** 证书编码。 */
    private String certificateCode;
    /** 网关编码。 */
    private String gatewayCode;
    /** 证书版本。 */
    private String certificateVersion;
    /** 到期日。 */
    private String expireAt;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
