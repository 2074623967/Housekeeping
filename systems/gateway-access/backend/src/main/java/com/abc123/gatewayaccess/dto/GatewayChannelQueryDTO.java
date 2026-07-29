package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 网关渠道查询条件。
 */
@Data
public class GatewayChannelQueryDTO {

    /** 关键字。 */
    private String keyword;
    /** 渠道类型。 */
    private String channelType = "全部";
    /** 状态。 */
    private String status = "全部";
}
