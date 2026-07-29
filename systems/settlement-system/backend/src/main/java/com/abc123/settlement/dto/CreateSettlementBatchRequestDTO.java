package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 创建结算批次请求。
 */
@Data
public class CreateSettlementBatchRequestDTO {

    private String batchDate;
    private String settlementType;
    private String createdBy;
    private String idempotencyKey;
}
