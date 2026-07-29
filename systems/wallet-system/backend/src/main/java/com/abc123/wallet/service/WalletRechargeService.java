package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;

public interface WalletRechargeService {
    WalletRechargeOrderDTO recharge(WalletRechargeRequestDTO request);
}
