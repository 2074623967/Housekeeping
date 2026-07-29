package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包流水信息。 */
@Data
public class WalletLedgerDTO {
    /** 流水号。 */
    private String ledgerNo;
    /** 钱包账户号。 */
    private String accountNo;
    /** 业务类型。 */
    private String bizType;
    /** 业务单号。 */
    private String bizNo;
    /** 金额。 */
    private String amount;
    /** 方向。 */
    private String direction;
    /** 创建时间。 */
    private String createdAt;
}
