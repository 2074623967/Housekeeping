package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包余额支付单。 */
@Data
public class WalletBalancePaymentOrderDTO {
    /** 支付单号。 */
    private String balancePaymentNo;
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 支付金额。 */
    private String amount;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
