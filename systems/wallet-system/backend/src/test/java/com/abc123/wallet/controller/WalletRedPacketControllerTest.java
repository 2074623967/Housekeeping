package com.abc123.wallet.controller;

import static org.mockito.Mockito.verify;

import com.abc123.wallet.dto.WalletRedPacketRequestDTO;
import com.abc123.wallet.service.WalletRedPacketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 红包控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class WalletRedPacketControllerTest {

    @Mock
    private WalletRedPacketService walletRedPacketService;

    @Test
    void shouldListRedPackets() {
        WalletRedPacketController controller = new WalletRedPacketController(walletRedPacketService);

        controller.list();

        verify(walletRedPacketService).listRedPackets();
    }

    @Test
    void shouldIssueRedPacket() {
        WalletRedPacketController controller = new WalletRedPacketController(walletRedPacketService);
        WalletRedPacketRequestDTO request = new WalletRedPacketRequestDTO();

        controller.issue(request);

        verify(walletRedPacketService).issue(request);
    }
}
