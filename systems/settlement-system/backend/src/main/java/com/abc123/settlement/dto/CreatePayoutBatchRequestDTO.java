package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 创建出款批次请求。
 */
@Data
public class CreatePayoutBatchRequestDTO {

    private String batchNo;
    private String payoutChannel;
    private String createdBy;
}
