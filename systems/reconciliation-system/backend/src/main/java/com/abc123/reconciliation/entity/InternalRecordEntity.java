package com.abc123.reconciliation.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 平台内部事实实体。
 */
@Data
public class InternalRecordEntity {

    /** 数据库主键。 */
    private Long id;
    /** 批次号。 */
    private String batchNo;
    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 平台金额。 */
    private BigDecimal amount;
    /** 平台状态。 */
    private String internalStatus;
    /** 来源系统。 */
    private String sourceSystem;
    /** 支付成功时间。 */
    private LocalDateTime paidTime;
}

