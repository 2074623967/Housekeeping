package com.abc123.wallet.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钱包充值请求。 */
@Data
public class WalletRechargeRequestDTO {
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 充值金额。 */
    private BigDecimal amount;
    /** 操作人。 */
    private String operatorName;
}
