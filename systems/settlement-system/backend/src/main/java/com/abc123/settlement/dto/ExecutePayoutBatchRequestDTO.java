package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 执行出款批次请求。
 */
@Data
public class ExecutePayoutBatchRequestDTO {

    /**
     * 执行人。
     */
    private String operatorName;

    /**
     * 执行备注。
     */
    private String remark;
}
