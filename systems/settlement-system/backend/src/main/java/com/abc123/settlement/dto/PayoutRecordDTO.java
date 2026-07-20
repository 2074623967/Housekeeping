package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 出款记录展示对象。
 */
@Data
public class PayoutRecordDTO {

    private String payoutNo;
    private String payoutBatchNo;
    private String settlementNo;
    private String targetNo;
    private String targetName;
    private String payoutAmount;
    private String payoutStatus;
    private String payoutStatusType;
    private String retryCount;
    private String createdAt;
}
