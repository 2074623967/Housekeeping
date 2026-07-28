package com.abc123.wallet.entity;

import lombok.Data;

/** 钱包余额支付单实体。 */
@Data
public class WalletBalancePaymentOrderEntity {
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
