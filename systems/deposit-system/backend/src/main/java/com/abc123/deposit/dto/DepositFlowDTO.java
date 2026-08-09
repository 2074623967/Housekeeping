package com.abc123.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 保证金流水视图。
 */
@Data
public class DepositFlowDTO {

    /** 流水号。 */
    private String flowNo;
    /** 账户号。 */
    private String accountNo;
    /** 动作类型。 */
    private String flowType;
    /** 发生金额。 */
    private BigDecimal amount;
    /** 变更前余额。 */
    private BigDecimal beforeBalance;
    /** 变更后余额。 */
    private BigDecimal afterBalance;
    /** 变更前冻结金额。 */
    private BigDecimal beforeFrozenAmount;
    /** 变更后冻结金额。 */
    private BigDecimal afterFrozenAmount;
    /** 业务关联号。 */
    private String referenceNo;
    /** 备注。 */
    private String remark;
    /** 发生时间。 */
    private LocalDateTime createdAt;
}
