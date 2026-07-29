package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付日终差异告警项。
 */
@Data
public class PaymentDayEndAlertItemDTO {

    /** 告警类型。 */
    private String alertType;
    /** 告警标题。 */
    private String alertTitle;
    /** 严重等级。 */
    private String severityLevel;
    /** 严重等级样式。 */
    private String severityLevelType;
    /** 影响数量。 */
    private Integer affectedCount;
    /** 差异说明。 */
    private String alertMessage;
    /** 建议动作。 */
    private String suggestedAction;
    /** 推荐跳转路由。 */
    private String actionRoute;
}
