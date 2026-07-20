package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分事件展示对象。
 */
@Data
public class ClearingEventDTO {

    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String statusType;
    private String createdAt;
}
