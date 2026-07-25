package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 异常告警值班路由实体，对应表：t_payment_issue_duty_roster。
 */
@Data
public class PaymentIssueDutyRosterEntity {

    /** 值班路由编码。 */
    private String rosterCode;
    /** 适用异常类型。 */
    private String issueType;
    /** 严重等级。 */
    private String severity;
    /** 责任组。 */
    private String responsibilityGroup;
    /** 值班接收人。 */
    private String receiver;
    /** 通知通道列表。 */
    private String notifyChannels;
    /** 升级等级。 */
    private String escalationLevel;
    /** 班次标签。 */
    private String scheduleTag;
    /** 班次生效开始小时。 */
    private Integer effectiveStartHour;
    /** 班次生效结束小时。 */
    private Integer effectiveEndHour;
    /** 适用星期范围，1-7 对应周一到周日。 */
    private String weekdayScope;
    /** 日期策略：ALL_DAYS/WORKDAY_ONLY/NON_WORKDAY_ONLY。 */
    private String holidayStrategy;
    /** 状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
}
