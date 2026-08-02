package com.abc123.walletaccount.dto;

import java.util.List;
import lombok.Data;

@Data
public class WalletAccountDetailDTO {

    /** 钱包账户基础信息。 */
    private WalletAccountDTO account;
    /** 钱包余额信息。 */
    private WalletBalanceDTO balance;
    /** 最近钱包流水。 */
    private List<WalletFlowDTO> recentFlows;
    /** 账户状态变更日志。 */
    private List<WalletAccountStatusLogDTO> statusLogs;
}
