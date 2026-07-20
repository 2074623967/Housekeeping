package com.abc123.clearing.entity;

import lombok.Data;

/**
 * 清分事件实体。
 */
@Data
public class ClearingEventEntity {

    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String createdAt;
}
