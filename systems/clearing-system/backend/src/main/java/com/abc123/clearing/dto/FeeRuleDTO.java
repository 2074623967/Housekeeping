package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 费用规则展示对象。
 */
@Data
public class FeeRuleDTO {

    private String feeRuleNo;
    private String feeName;
    private String feeType;
    private String feeMode;
    private String feeRate;
    private String fixedAmount;
    private String feeBearer;
    private String status;
    private String statusType;
    private String createdAt;
}
