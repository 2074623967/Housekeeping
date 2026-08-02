package com.abc123.walletaccount.dto;

import java.util.List;
import lombok.Data;

@Data
public class WalletAccountDetailDTO {

    private WalletAccountDTO account;
    private WalletBalanceDTO balance;
    private List<WalletFlowDTO> recentFlows;
}
