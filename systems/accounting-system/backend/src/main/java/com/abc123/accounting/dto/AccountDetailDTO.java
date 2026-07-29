package com.abc123.accounting.dto;

import java.util.List;
import lombok.Data;

/**
 * 账户详情对象。
 */
@Data
public class AccountDetailDTO {

    private AccountListItemDTO account;
    private BalanceSnapshotDTO balance;
    private List<LedgerItemDTO> recentLedgers;
}
