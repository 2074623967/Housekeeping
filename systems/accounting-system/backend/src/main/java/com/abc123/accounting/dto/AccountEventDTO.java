package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 账户事件对象。
 */
@Data
public class AccountEventDTO {

    private String eventNo;
    private String eventType;
    private String bizNo;
    private String eventStatus;
    private String statusType;
    private String summary;
    private String payload;
    private String createdAt;
}
