package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 重试出款批次请求。
 */
@Data
public class RetryPayoutBatchRequestDTO {

    private String operatorName;
    private String reason;
}
