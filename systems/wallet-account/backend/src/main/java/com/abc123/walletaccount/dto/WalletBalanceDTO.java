package com.abc123.walletaccount.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WalletBalanceDTO {

    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 总余额。 */
    private BigDecimal totalBalance;
    /** 可用余额。 */
    private BigDecimal availableBalance;
    /** 冻结余额。 */
    private BigDecimal frozenBalance;
    /** 在途入账金额。 */
    private BigDecimal pendingInBalance;
    /** 在途出账金额。 */
    private BigDecimal pendingOutBalance;
}
