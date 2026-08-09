package com.abc123.settlement.controller;

import static org.mockito.Mockito.verify;

import com.abc123.settlement.service.SettlementDashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 结算工作台控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementDashboardControllerTest {

    @Mock
    private SettlementDashboardService settlementDashboardService;

    @Test
    void shouldReturnDashboard() {
        SettlementDashboardController controller = new SettlementDashboardController(settlementDashboardService);

        controller.dashboard();

        verify(settlementDashboardService).dashboard();
    }
}
