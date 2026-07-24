package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常告警值班路由配置。
 */
@Data
public class PaymentIssueDutyRosterDTO {

    /** 路由编码。 */
    private String rosterCode;
    /** 适用异常类型。 */
    private String issueType;
    /** 适用严重等级。 */
    private String severity;
    /** 责任组。 */
    private String responsibilityGroup;
    /** 值班接收人。 */
    private String receiver;
    /** 触达通道。 */
    private String notifyChannels;
    /** 升级等级。 */
    private String escalationLevel;
    /** 值班班次说明。 */
    private String scheduleTag;
    /** 班次生效开始小时。 */
    private Integer effectiveStartHour;
    /** 班次生效结束小时。 */
    private Integer effectiveEndHour;
    /** 班次生效时间窗。 */
    private String effectiveWindow;
    /** 配置状态。 */
    private String status;
    /** 配置状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
