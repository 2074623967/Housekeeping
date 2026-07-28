package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletTransferOrderDTO;
import com.abc123.wallet.dto.WalletTransferRequestDTO;

public interface WalletTransferService {
    WalletTransferOrderDTO transfer(WalletTransferRequestDTO request);
}
