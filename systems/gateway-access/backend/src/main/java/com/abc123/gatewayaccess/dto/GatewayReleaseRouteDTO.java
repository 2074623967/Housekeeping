package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 灰度发布路由台账。
 */
@Data
public class GatewayReleaseRouteDTO {

    /** 路由编码。 */
    private String routeCode;
    /** 网关编码。 */
    private String gatewayCode;
    /** 环境。 */
    private String environment;
    /** 发布策略。 */
    private String releaseStrategy;
    /** 流量百分比。 */
    private Integer trafficPercent;
    /** 发布窗口。 */
    private String releaseWindow;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
