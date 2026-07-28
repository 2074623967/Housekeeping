package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 灰度发布路由查询条件。
 */
@Data
public class GatewayReleaseRouteQueryDTO {

    /** 环境。 */
    private String environment = "全部";
    /** 状态。 */
    private String status = "全部";
}
