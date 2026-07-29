package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付异常告警项。
 */
@Data
public class PaymentAlertItemDTO {

    /** 告警类型。 */
    private String alertType;
    /** 告警标题。 */
    private String alertTitle;
    /** 告警内容。 */
    private String alertMessage;
    /** 告警等级。 */
    private String alertLevel;
    /** 告警等级样式。 */
    private String alertLevelType;
    /** 影响笔数。 */
    private Integer affectedCount;
    /** 建议动作。 */
    private String suggestedAction;
    /** 建议跳转页面。 */
    private String actionRoute;
}
