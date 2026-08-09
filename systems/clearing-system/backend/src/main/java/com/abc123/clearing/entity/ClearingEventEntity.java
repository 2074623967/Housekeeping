package com.abc123.clearing.entity;

import lombok.Data;

/**
 * 清分事件实体。
 */
@Data
public class ClearingEventEntity {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    private String eventNo;
    private String eventType;
    private String bizNo;
    private String summary;
    private String payload;
    private String eventStatus;
    private String publishStatus;
    private Integer retryCount;
    private String lastPublishedAt;
    private String nextRetryAt;
    private String createdAt;
}
