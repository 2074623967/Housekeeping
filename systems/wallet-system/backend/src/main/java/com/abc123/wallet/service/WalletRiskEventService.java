package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletRiskApprovalRequestDTO;
import com.abc123.wallet.dto.WalletRiskEventDTO;
import java.util.List;

public interface WalletRiskEventService {
    List<WalletRiskEventDTO> listRiskEvents();

    WalletRiskEventDTO approve(WalletRiskApprovalRequestDTO request);
}
