package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包转账单。 */
@Data
public class WalletTransferOrderDTO {
    /** 转账单号。 */
    private String transferNo;
    /** 转出账户号。 */
    private String sourceAccountNo;
    /** 转入账户号。 */
    private String targetAccountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 转账金额。 */
    private String amount;
    /** 处理状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
