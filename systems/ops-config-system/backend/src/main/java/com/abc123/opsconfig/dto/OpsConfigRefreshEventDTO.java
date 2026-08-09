package com.abc123.opsconfig.dto;

import lombok.Data;

/**
 * 配置快照刷新事件。
 */
@Data
public class OpsConfigRefreshEventDTO {

    /** 刷新事件号。 */
    private String eventNo;
    /** 配置快照版本号。 */
    private Long snapshotVersion;
    /** 灰度范围。 */
    private String grayScope;
    /** 事件状态。 */
    private String status;
    /** 触发来源申请号。 */
    private String sourceRequestNo;
    /** 创建时间。 */
    private String createdAt;
    /** 消费确认人。 */
    private String acknowledgedBy;
    /** 消费确认时间。 */
    private String acknowledgedAt;
}
