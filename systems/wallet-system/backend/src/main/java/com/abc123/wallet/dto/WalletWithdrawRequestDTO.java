package com.abc123.wallet.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钱包提现请求。 */
@Data
public class WalletWithdrawRequestDTO {
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 提现金额。 */
    private BigDecimal amount;
    /** 操作人。 */
    private String operatorName;
}
