package com.abc123.deposit.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金账户实体，对应 t_deposit_account。
 */
@Data
public class DepositAccountEntity {

    /** 主键。 */
    private Long id;
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
    /** 账户状态。 */
    private String status;
}
