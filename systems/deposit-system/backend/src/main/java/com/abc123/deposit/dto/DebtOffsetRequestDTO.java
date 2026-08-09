package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金抵扣欠款请求。
 */
@Data
public class DebtOffsetRequestDTO {

    /** 保证金账户号。 */
    private String accountNo;
    /** 欠款单号。 */
    private String debtNo;
    /** 欠款金额。 */
    private BigDecimal debtAmount;
    /** 操作备注。 */
    private String remark;
}
