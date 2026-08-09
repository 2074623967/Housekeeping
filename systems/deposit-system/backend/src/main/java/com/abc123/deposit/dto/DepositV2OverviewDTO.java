package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金 V2 演进概览。
 */
@Data
public class DepositV2OverviewDTO {

    /** 总保证金账户数。 */
    private Integer totalAccountCount;
    /** 规则绑定预览数。 */
    private Integer bindingCount;
    /** 旧账户结转候选数。 */
    private Integer migrationCandidateCount;
    /** 总应缴金额。 */
    private BigDecimal totalRequiredAmount;
    /** 总余额。 */
    private BigDecimal totalBalance;
    /** 总冻结金额。 */
    private BigDecimal totalFrozenAmount;
    /** 总可用金额。 */
    private BigDecimal totalAvailableAmount;
}
