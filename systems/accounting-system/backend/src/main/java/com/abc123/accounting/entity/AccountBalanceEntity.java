package com.abc123.accounting.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 账户余额快照实体。
 */
@Data
public class AccountBalanceEntity {

    /** 账户号。 */
    private String accountNo;
    /** 可用余额。 */
    private BigDecimal availableAmount;
    /** 冻结余额。 */
    private BigDecimal frozenAmount;
    /** 在途余额。 */
    private BigDecimal inTransitAmount;
    /** 更新时间。 */
    private String updatedAt;
}
