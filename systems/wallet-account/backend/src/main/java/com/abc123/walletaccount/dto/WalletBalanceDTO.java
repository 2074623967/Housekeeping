package com.abc123.walletaccount.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class WalletBalanceDTO {

    private String walletAccountNo;
    private BigDecimal totalBalance;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal pendingInBalance;
    private BigDecimal pendingOutBalance;
}
