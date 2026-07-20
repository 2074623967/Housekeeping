package com.abc123.settlement.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 结算明细实体。
 */
@Data
public class SettlementItemEntity {

    private String itemNo;
    private String settlementNo;
    private String itemType;
    private String itemName;
    private BigDecimal itemAmount;
    private String createdAt;
}
