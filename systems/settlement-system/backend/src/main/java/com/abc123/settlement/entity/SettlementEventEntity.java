package com.abc123.settlement.entity;

import lombok.Data;

/**
 * 结算事件实体。
 */
@Data
public class SettlementEventEntity {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String createdAt;
}
