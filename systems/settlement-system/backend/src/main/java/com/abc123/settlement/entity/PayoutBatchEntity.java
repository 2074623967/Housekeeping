package com.abc123.settlement.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 出款批次实体。
 */
@Data
public class PayoutBatchEntity {

    private String payoutBatchNo;
    private String batchNo;
    private String payoutChannel;
    private String payoutStatus;
    private int payoutCount;
    private int successCount;
    private int failedCount;
    private BigDecimal totalAmount;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
}
