package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 跨系统事实快照展示对象。
 */
@Data
public class CrossSystemFactDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    /** 来源系统编码。 */
    private String systemCode;
    /** 事实类型。 */
    private String factType;
    /** 来源事实编号。 */
    private String factNo;
    /** 来源业务编号。 */
    private String businessNo;
    /** 事实状态。 */
    private String factStatus;
    /** 事实金额。 */
    private BigDecimal amount;
    /** 来源配置快照版本。 */
    private Long snapshotVersion;
    /** 事实发生时间。 */
    private LocalDateTime occurredAt;
    /** 事实载荷。 */
    private String payload;
    /** 记录时间。 */
    private LocalDateTime createdAt;
}
