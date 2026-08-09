package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分事件展示对象。
 */
@Data
public class ClearingEventDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    /** 事件号。 */
    private String eventNo;
    /** 事件类型。 */
    private String eventType;
    /** 业务单号。 */
    private String bizNo;
    /** 摘要。 */
    private String summary;
    /** 载荷。 */
    private String payload;
    /** 事件状态。 */
    private String eventStatus;
    /** 事件状态标签。 */
    private String statusType;
    /** 下游派发状态。 */
    private String publishStatus;
    /** 重试次数。 */
    private Integer retryCount;
    /** 下次重试时间。 */
    private String nextRetryAt;
    /** 处置建议。 */
    private String replayHint;
    /** 创建时间。 */
    private String createdAt;
}
