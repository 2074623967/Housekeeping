package com.abc123.accounting.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 账户流水实体。
 */
@Data
public class AccountLedgerEntity {

    /** 流水号。 */
    private String ledgerNo;
    /** 账户号。 */
    private String accountNo;
    /** 业务类型。 */
    private String bizType;
    /** 业务单号。 */
    private String bizNo;
    /** 借贷方向。 */
    private String direction;
    /** 变更金额。 */
    private BigDecimal amount;
    /** 变更前余额。 */
    private BigDecimal beforeBalance;
    /** 变更后余额。 */
    private BigDecimal afterBalance;
    /** 流水状态。 */
    private String ledgerStatus;
    /** 操作人。 */
    private String operatorName;
    /** 创建时间。 */
    private String createdAt;
}
