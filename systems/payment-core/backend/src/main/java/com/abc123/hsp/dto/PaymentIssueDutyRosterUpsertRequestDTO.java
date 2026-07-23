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
    /** 班次标签。 */
    private String scheduleTag;
    /** 是否启用。 */
    private Boolean enabled;
}
