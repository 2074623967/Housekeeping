package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对账批次中的平台支付事实。
 */
@Data
public class ReconciliationBatchInternalRecordDTO {

    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 平台金额。 */
    private BigDecimal amount;
    /** 平台支付状态。 */
    private String internalStatus;
    /** 来源系统。 */
    private String sourceSystem;
    /** 支付成功时间。 */
    private LocalDateTime paidTime;
}
