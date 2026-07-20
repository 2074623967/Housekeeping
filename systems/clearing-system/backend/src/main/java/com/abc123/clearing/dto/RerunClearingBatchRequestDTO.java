package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 重跑清分批次请求。
 */
@Data
public class RerunClearingBatchRequestDTO {

    private String operatorName;
    private String reason;
}
