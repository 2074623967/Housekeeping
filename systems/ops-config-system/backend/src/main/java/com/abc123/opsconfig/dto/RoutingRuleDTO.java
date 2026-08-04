package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 路由规则视图。
 */
@Data
public class RoutingRuleDTO {

    /** 路由编码。 */
    private String routeCode;
    /** 业务线编码。 */
    private String businessCode;
    /** 支付类型。 */
    private String payType;
    /** 优先渠道。 */
    private String primaryChannel;
    /** 备选渠道。 */
    private String backupChannel;
    /** 命中策略。 */
    private String matchPolicy;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
