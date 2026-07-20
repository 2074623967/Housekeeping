package com.abc123.clearing.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分批次实体。
 */
@Data
public class ClearingBatchEntity {

    private String batchNo;
    private String batchDate;
    private String sourceType;
    private String batchStatus;
    private int totalOrderCount;
    private int successOrderCount;
    private int failedOrderCount;
    private BigDecimal totalAmount;
    private String versionNo;
    private String createdBy;
    private String createdAt;
    private String finishedAt;
    private String idempotencyKey;
}
