package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 账户事件对象。
 */
@Data
public class AccountEventDTO {

    /** 事件号。 */
    private String eventNo;
    /** 事件类型。 */
    private String eventType;
    /** 业务单号。 */
    private String bizNo;
    /** 事件状态。 */
    private String eventStatus;
    /** 状态样式。 */
    private String statusType;
    /** 事件摘要。 */
    private String summary;
    /** 事件载荷。 */
    private String payload;
    /** 补偿排查提示。 */
    private String compensationHint;
    /** 创建时间。 */
    private String createdAt;
}
