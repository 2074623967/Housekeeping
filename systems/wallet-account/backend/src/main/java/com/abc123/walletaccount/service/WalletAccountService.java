package com.abc123.walletaccount.service;

import com.abc123.walletaccount.dto.OpenWalletAccountRequestDTO;
import com.abc123.walletaccount.dto.PageResultDTO;
import com.abc123.walletaccount.dto.WalletAccountDTO;
import com.abc123.walletaccount.dto.WalletAccountDetailDTO;
import com.abc123.walletaccount.dto.WalletAccountQueryDTO;
import com.abc123.walletaccount.dto.WalletAccountStatusChangeRequestDTO;
import com.abc123.walletaccount.dto.WalletBalanceDTO;
import com.abc123.walletaccount.dto.WalletFlowDTO;
import com.abc123.walletaccount.dto.WalletFlowQueryDTO;
import java.util.List;

public interface WalletAccountService {

    PageResultDTO<WalletAccountDTO> pageAccounts(WalletAccountQueryDTO queryDTO);

    WalletAccountDetailDTO getAccountDetail(String walletAccountNo);

    WalletBalanceDTO getBalance(String walletAccountNo);

    List<WalletBalanceDTO> listBalances(List<String> walletAccountNos);

    List<WalletFlowDTO> listFlows(WalletFlowQueryDTO queryDTO);

    WalletAccountDTO openAccount(OpenWalletAccountRequestDTO requestDTO);

    WalletAccountDTO changeStatus(String walletAccountNo, WalletAccountStatusChangeRequestDTO requestDTO);
}
