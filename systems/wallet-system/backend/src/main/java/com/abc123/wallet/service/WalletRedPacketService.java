package com.abc123.wallet.service;

import com.abc123.wallet.dto.WalletRedPacketDTO;
import com.abc123.wallet.dto.WalletRedPacketRequestDTO;
import java.util.List;

public interface WalletRedPacketService {
    List<WalletRedPacketDTO> listRedPackets();

    WalletRedPacketDTO issue(WalletRedPacketRequestDTO request);
}
