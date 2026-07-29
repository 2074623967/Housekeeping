package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 结算批次展示对象。
 */
@Data
public class SettlementBatchDTO {

    private String batchNo;
    private String batchDate;
    private String settlementType;
    private String batchStatus;
    private String batchStatusType;
    private int totalCount;
    private int auditedCount;
    private int payoutCount;
    private String totalAmount;
    private String payoutChannel;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
}
