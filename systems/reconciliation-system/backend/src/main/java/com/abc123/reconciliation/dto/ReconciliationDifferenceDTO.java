package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对账差异项。
 */
@Data
public class ReconciliationDifferenceDTO {

    /** 差异编号。 */
    private String differenceNo;
    /** 批次号。 */
    private String batchNo;
    /** 差异类型。 */
    private String differenceType;
    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 渠道金额。 */
    private BigDecimal channelAmount;
    /** 平台金额。 */
    private BigDecimal internalAmount;
    /** 差异状态。 */
    private String status;
    /** 处置结论。 */
    private String resolution;
    /** 处置备注。 */
    private String remark;
    /** 发现时间。 */
    private LocalDateTime detectedAt;
    /** 结案时间。 */
    private LocalDateTime resolvedAt;
}

