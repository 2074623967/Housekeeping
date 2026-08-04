package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金账户视图。
 */
@Data
public class DepositAccountDTO {

    /** 账户号。 */
    private String accountNo;
    /** 主体编号。 */
    private String ownerId;
    /** 主体类型。 */
    private String ownerType;
    /** 应缴金额。 */
    private BigDecimal requiredAmount;
    /** 已缴余额。 */
    private BigDecimal balance;
    /** 冻结金额。 */
    private BigDecimal frozenAmount;
    /** 可用余额。 */
    private BigDecimal availableAmount;
    /** 账户状态。 */
    private String status;
}
