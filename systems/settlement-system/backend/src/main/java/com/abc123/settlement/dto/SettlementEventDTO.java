package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 结算事件展示对象。
 */
@Data
public class SettlementEventDTO {

    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String statusType;
    private String createdAt;
}
