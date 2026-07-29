package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建调账单请求。
 */
@Data
public class CreateAdjustmentRequestDTO {

    private String accountNo;
    private String adjustDirection;
    private BigDecimal adjustAmount;
    private String adjustReason;
    private String createdBy;
}
