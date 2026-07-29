package com.abc123.settlement.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 出款记录实体。
 */
@Data
public class PayoutRecordEntity {

    private String payoutNo;
    private String payoutBatchNo;
    private String settlementNo;
    private String targetNo;
    private String targetName;
    private BigDecimal payoutAmount;
    private String payoutStatus;
    private int retryCount;
    private String createdAt;
}
