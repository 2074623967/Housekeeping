package com.abc123.wallet.dto;

import java.util.List;
import lombok.Data;

/** 钱包账户详情。 */
@Data
public class WalletAccountDetailDTO {
    /** 钱包账户信息。 */
    private WalletAccountDTO account;
    /** 最近流水。 */
    private List<WalletLedgerDTO> ledgers;
}
