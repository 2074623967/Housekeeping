package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分批次展示对象。
 */
@Data
public class ClearingBatchDTO {

    private String batchNo;
    private String batchDate;
    private String sourceType;
    private String batchStatus;
    private String batchStatusType;
    private int totalOrderCount;
    private int successOrderCount;
    private int failedOrderCount;
    private String totalAmount;
    private String versionNo;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
}
