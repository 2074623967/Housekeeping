package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletBalancePaymentOrderDTO;
import com.abc123.wallet.dto.WalletBalancePaymentRequestDTO;

public interface WalletBalancePaymentService {
    WalletBalancePaymentOrderDTO pay(WalletBalancePaymentRequestDTO request);
}
