package com.abc123.wallet.entity;

import lombok.Data;

/** 钱包提现单实体。 */
@Data
public class WalletWithdrawOrderEntity {
    /** 提现单号。 */
    private String withdrawNo;
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 提现金额。 */
    private String amount;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
