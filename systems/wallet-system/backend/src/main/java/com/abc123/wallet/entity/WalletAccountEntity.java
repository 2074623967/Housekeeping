package com.abc123.wallet.entity;

import lombok.Data;

/** 钱包账户实体。 */
@Data
public class WalletAccountEntity {
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
    /** 创建时间。 */
    private String createdAt;
}
