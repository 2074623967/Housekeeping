package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 出款批次展示对象。
 */
@Data
public class PayoutBatchDTO {

    private String payoutBatchNo;
    private String batchNo;
    private String payoutChannel;
    private String payoutStatus;
    private String payoutStatusType;
    private int payoutCount;
    private int successCount;
    private int failedCount;
    private String totalAmount;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
}
