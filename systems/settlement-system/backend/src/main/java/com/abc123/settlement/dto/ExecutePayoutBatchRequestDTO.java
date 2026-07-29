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

    /**
     * 执行结果，支持 SUCCESS / FAILED。
     */
    private String executionResult;

    /**
     * 失败原因。
     */
    private String failureReason;
}
