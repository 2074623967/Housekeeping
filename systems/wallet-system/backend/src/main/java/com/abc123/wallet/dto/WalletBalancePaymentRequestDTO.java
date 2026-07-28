package com.abc123.wallet.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钱包余额支付请求。 */
@Data
public class WalletBalancePaymentRequestDTO {
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 支付金额。 */
    private BigDecimal amount;
    /** 操作人。 */
    private String operatorName;
    /** 备注。 */
    private String remark;
}
