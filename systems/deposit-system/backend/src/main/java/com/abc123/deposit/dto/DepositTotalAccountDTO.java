package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 总保证金账户预览。
 */
@Data
public class DepositTotalAccountDTO {

    /** 总保证金账户号。 */
    private String totalAccountNo;
    /** 归集主体类型。 */
    private String ownerType;
    /** 账户数量。 */
    private Integer accountCount;
    /** 规则数量。 */
    private Integer ruleCount;
    /** 迁移候选数。 */
    private Integer migrationCount;
    /** 总应缴金额。 */
    private BigDecimal totalRequiredAmount;
    /** 总余额。 */
    private BigDecimal totalBalance;
    /** 总冻结金额。 */
    private BigDecimal totalFrozenAmount;
    /** 总可用金额。 */
    private BigDecimal totalAvailableAmount;
    /** 总账户状态。 */
    private String status;
}
