package com.abc123.settlement.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 结算批次实体。
 */
@Data
public class SettlementBatchEntity {

    private String batchNo;
    private String batchDate;
    private String settlementType;
    private String batchStatus;
    private int totalCount;
    private int auditedCount;
    private int payoutCount;
    private BigDecimal totalAmount;
    private String payoutChannel;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
    private String idempotencyKey;
}
