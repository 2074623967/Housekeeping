package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletWithdrawOrderDTO;
import com.abc123.wallet.dto.WalletWithdrawRequestDTO;

public interface WalletWithdrawService {
    WalletWithdrawOrderDTO withdraw(WalletWithdrawRequestDTO request);
}
