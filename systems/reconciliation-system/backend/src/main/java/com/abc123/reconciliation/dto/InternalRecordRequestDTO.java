package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 平台内部支付事实记录。
 */
@Data
public class InternalRecordRequestDTO {

    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 平台金额。 */
    private BigDecimal amount;
    /** 平台交易状态。 */
    private String internalStatus;
    /** 来源系统。 */
    private String sourceSystem;
    /** 支付成功时间。 */
    private LocalDateTime paidTime;
}

