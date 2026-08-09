package com.abc123.wallet.controller;

import static org.mockito.Mockito.verify;

import com.abc123.wallet.dto.WalletRiskApprovalRequestDTO;
import com.abc123.wallet.service.WalletRiskEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 钱包风险事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class WalletRiskEventControllerTest {

    @Mock
    private WalletRiskEventService walletRiskEventService;

    @Test
    void shouldListRiskEvents() {
        WalletRiskEventController controller = new WalletRiskEventController(walletRiskEventService);

        controller.list();

        verify(walletRiskEventService).listRiskEvents();
    }

    @Test
    void shouldApproveRiskEvent() {
        WalletRiskEventController controller = new WalletRiskEventController(walletRiskEventService);
        WalletRiskApprovalRequestDTO request = new WalletRiskApprovalRequestDTO();

        controller.approve(request);

        verify(walletRiskEventService).approve(request);
    }
}
