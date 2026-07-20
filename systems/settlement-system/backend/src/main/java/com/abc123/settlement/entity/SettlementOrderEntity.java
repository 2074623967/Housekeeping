package com.abc123.settlement.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 结算单实体。
 */
@Data
public class SettlementOrderEntity {

    private String settlementNo;
    private String batchNo;
    private String targetType;
    private String targetNo;
    private String targetName;
    private BigDecimal shouldSettleAmount;
    private BigDecimal deductAmount;
    private BigDecimal netSettleAmount;
    private String settlementStatus;
    private String payoutStatus;
    private String auditStatus;
    private String createdAt;
    private String clearingNo;
}
