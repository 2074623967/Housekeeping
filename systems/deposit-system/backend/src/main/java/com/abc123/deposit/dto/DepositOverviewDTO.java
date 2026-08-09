package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金工作台概览。
 */
@Data
public class DepositOverviewDTO {

    /** 账户总数。 */
    private Integer accountCount;
    /** 开户主体数。 */
    private Integer ownerCount;
    /** 余额总额。 */
    private BigDecimal totalBalance;
    /** 冻结总额。 */
    private BigDecimal totalFrozenAmount;
    /** 可用总额。 */
    private BigDecimal totalAvailableAmount;
    /** 已缴达标账户数。 */
    private Integer fullyPaidAccountCount;
}
