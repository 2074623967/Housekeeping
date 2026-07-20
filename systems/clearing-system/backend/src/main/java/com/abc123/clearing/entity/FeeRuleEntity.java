package com.abc123.clearing.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 费用规则实体。
 */
@Data
public class FeeRuleEntity {

    private String feeRuleNo;
    private String feeName;
    private String feeType;
    private String feeMode;
    private BigDecimal feeRate;
    private BigDecimal fixedAmount;
    private String feeBearer;
    private String status;
    private String createdAt;
}
