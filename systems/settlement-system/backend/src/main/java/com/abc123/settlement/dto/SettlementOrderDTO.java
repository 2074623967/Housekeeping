package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 结算单展示对象。
 */
@Data
public class SettlementOrderDTO {

    private String settlementNo;
    private String batchNo;
    private String targetType;
    private String targetNo;
    private String targetName;
    private String shouldSettleAmount;
    private String deductAmount;
    private String netSettleAmount;
    private String settlementStatus;
    private String settlementStatusType;
    private String payoutStatus;
    private String payoutStatusType;
    private String auditStatus;
    private String createdAt;
}
