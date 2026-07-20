package com.abc123.accounting.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 账户冻结单实体。
 */
@Data
public class AccountFreezeEntity {

    /** 冻结单号。 */
    private String freezeNo;
    /** 账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 冻结类型。 */
    private String freezeType;
    /** 冻结原因。 */
    private String freezeReason;
    /** 冻结金额。 */
    private BigDecimal freezeAmount;
    /** 冻结状态。 */
    private String freezeStatus;
    /** 操作人。 */
    private String operatorName;
    /** 创建时间。 */
    private String createdAt;
    /** 解冻时间。 */
    private String unfrozenAt;
}
