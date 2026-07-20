package com.abc123.settlement.entity;

import lombok.Data;

/**
 * 结算事件实体。
 */
@Data
public class SettlementEventEntity {

    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String createdAt;
}
