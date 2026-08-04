package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金资金动作请求。
 */
@Data
public class DepositActionRequestDTO {

    /** 保证金账户号。 */
    private String accountNo;
    /** 操作金额。 */
    private BigDecimal amount;
    /** 业务关联号。 */
    private String referenceNo;
    /** 操作备注。 */
    private String remark;
}
