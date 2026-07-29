package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletAccountDTO;
import com.abc123.wallet.dto.WalletAccountDetailDTO;
import com.abc123.wallet.dto.WalletLedgerDTO;
import java.util.List;

public interface WalletService {
    List<WalletAccountDTO> listAccounts();
    WalletAccountDetailDTO getDetail(String accountNo);
    List<WalletLedgerDTO> listLedgers(String accountNo, String bizType, String direction);
}
