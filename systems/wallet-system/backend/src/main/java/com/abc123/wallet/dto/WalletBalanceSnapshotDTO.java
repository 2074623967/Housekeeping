package com.abc123.wallet.dto;

import lombok.Data;

/** 钱包余额快照。 */
@Data
public class WalletBalanceSnapshotDTO {
    /** 钱包账户号。 */
    private String accountNo;
    /** 用户名称。 */
    private String ownerName;
    /** 钱包类型。 */
    private String walletType;
    /** 账户状态。 */
    private String status;
    /** 可用余额。 */
    private String availableAmount;
    /** 冻结余额。 */
    private String frozenAmount;
    /** 总余额。 */
    private String totalAmount;
    /** 冻结占比。 */
    private String frozenRate;
    /** 资金关注标签。 */
    private String focusTag;
    /** 快照时间。 */
    private String snapshotAt;
}
