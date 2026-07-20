package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 创建清分批次请求。
 */
@Data
public class CreateClearingBatchRequestDTO {

    private String batchDate;
    private String sourceType;
    private String createdBy;
    private String idempotencyKey;
}
