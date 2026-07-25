package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 异常告警值班路由新增或编辑请求。
 */
@Data
public class PaymentIssueDutyRosterUpsertRequestDTO {

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
    /** 升级接收人。 */
    private String escalationReceiver;
    /** 升级策略说明。 */
    private String escalationPolicy;
    /** 升级超时分钟数。 */
    private Integer escalationTimeoutMinutes;
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
    /** 是否启用。 */
    private Boolean enabled;
}
