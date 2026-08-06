package com.abc123.deposit.dto;

import lombok.Data;

/**
 * 保证金流水查询条件。
 */
@Data
public class DepositFlowQueryDTO {

    /** 流水类型。 */
    private String flowType;
    /** 业务关联号。 */
    private String referenceNo;
}
