package com.abc123.deposit.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 保证金账户详情视图。
 */
@Data
public class DepositAccountDetailDTO {

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
    /** 累计流水笔数。 */
    private Integer flowCount;
    /** 最近流水时间。 */
    private LocalDateTime latestFlowTime;
    /** 是否已缴达标。 */
    private Boolean fullyPaid;
    /** 待补缴金额。 */
    private BigDecimal outstandingAmount;
}
