package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 调账单对象。
 */
@Data
public class AdjustmentItemDTO {

    private String adjustNo;
    private String accountNo;
    private String adjustDirection;
    private String adjustAmount;
    private String adjustReason;
    private String adjustStatus;
    private String statusType;
    private String createdBy;
    private String approvedBy;
    private String createdAt;
    private String approvedAt;
}
