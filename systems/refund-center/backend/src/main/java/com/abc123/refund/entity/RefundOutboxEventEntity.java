package com.abc123.refund.entity;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 退款成功事件出站实体，对应 t_refund_outbox_event。
 */
@Data
public class RefundOutboxEventEntity {

    /** 数据库主键。 */
    private Long id;
    /** 事件编号。 */
    private String eventId;
    /** 事件类型。 */
    private String eventType;
    /** 聚合编号。 */
    private String aggregateId;
    /** 事件载荷。 */
    private String payloadJson;
    /** 发送状态。 */
    private String status;
    /** 重试次数。 */
    private Integer retryCount;
    /** 最近一次错误信息。 */
    private String lastErrorMessage;
    /** 最近一次派发时间。 */
    private LocalDateTime lastRelayAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
