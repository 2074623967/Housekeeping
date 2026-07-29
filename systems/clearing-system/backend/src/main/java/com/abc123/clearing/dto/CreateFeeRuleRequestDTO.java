package com.abc123.clearing.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建费用规则请求。
 */
@Data
public class CreateFeeRuleRequestDTO {

    private String feeName;
    private String feeType;
    private String feeMode;
    private BigDecimal feeRate;
    private BigDecimal fixedAmount;
    private String feeBearer;
}
