package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包充值单。 */
@Data
public class WalletRechargeOrderDTO {
    /** 充值单号。 */
    private String rechargeNo;
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 充值金额。 */
    private String amount;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
