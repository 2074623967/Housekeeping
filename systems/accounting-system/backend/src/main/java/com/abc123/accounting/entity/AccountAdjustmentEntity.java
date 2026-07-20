package com.abc123.accounting.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 账户调账单实体。
 */
@Data
public class AccountAdjustmentEntity {

    /** 调账单号。 */
    private String adjustNo;
    /** 账户号。 */
    private String accountNo;
    /** 调账方向。 */
    private String adjustDirection;
    /** 调账金额。 */
    private BigDecimal adjustAmount;
    /** 调账原因。 */
    private String adjustReason;
    /** 调账状态。 */
    private String adjustStatus;
    /** 创建人。 */
    private String createdBy;
    /** 审批人。 */
    private String approvedBy;
    /** 创建时间。 */
    private String createdAt;
    /** 审批时间。 */
    private String approvedAt;
}
