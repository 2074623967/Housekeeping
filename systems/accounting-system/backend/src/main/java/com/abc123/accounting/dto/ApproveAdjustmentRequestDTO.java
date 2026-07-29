package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 调账审批请求。
 */
@Data
public class ApproveAdjustmentRequestDTO {

    private String approvedBy;
}
