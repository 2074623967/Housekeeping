package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 结算明细项展示对象。
 */
@Data
public class SettlementItemDTO {

    private String itemNo;
    private String settlementNo;
    private String itemType;
    private String itemName;
    private String itemAmount;
    private String createdAt;
}
