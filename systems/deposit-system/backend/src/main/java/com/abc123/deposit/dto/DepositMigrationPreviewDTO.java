package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金旧账户结转预览。
 */
@Data
public class DepositMigrationPreviewDTO {

    /** 结转预览编号。 */
    private String migrationNo;
    /** 原保证金账户号。 */
    private String sourceAccountNo;
    /** 原主体编号。 */
    private String sourceOwnerId;
    /** 原主体类型。 */
    private String sourceOwnerType;
    /** 目标总保证金账户号。 */
    private String targetTotalAccountNo;
    /** 原账户余额。 */
    private BigDecimal sourceBalance;
    /** 预计结转金额。 */
    private BigDecimal migratedBalance;
    /** 结转状态。 */
    private String migrationStatus;
    /** 结转说明。 */
    private String migrationRemark;
}
