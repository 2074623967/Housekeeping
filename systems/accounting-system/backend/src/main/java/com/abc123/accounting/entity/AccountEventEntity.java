package com.abc123.accounting.entity;

import lombok.Data;

/**
 * 账户事件实体。
 */
@Data
public class AccountEventEntity {

    /** 事件号。 */
    private String eventNo;
    /** 事件类型。 */
    private String eventType;
    /** 业务单号。 */
    private String bizNo;
    /** 事件摘要。 */
    private String summary;
    /** 事件载荷。 */
    private String payload;
    /** 事件状态。 */
    private String eventStatus;
    /** 创建时间。 */
    private String createdAt;
}
