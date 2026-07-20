package com.abc123.gatewayaccess.entity;

import lombok.Data;

/**
 * 网关渠道实体。
 */
@Data
public class GatewayChannelEntity {

    /** 主键ID。 */
    private Long id;
    /** 网关编码。 */
    private String gatewayCode;
    /** 网关名称。 */
    private String gatewayName;
    /** 渠道类型。 */
    private String channelType;
    /** 协议类型。 */
    private String protocolType;
    /** 签名算法。 */
    private String signAlgorithm;
    /** 接入地址。 */
    private String endpoint;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
