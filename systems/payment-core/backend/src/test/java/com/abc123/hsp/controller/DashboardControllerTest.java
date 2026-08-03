package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.DashboardSummaryDTO;
import com.abc123.hsp.service.DashboardService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付工作台控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Test
    void shouldReturnDashboardSummary() {
        DashboardSummaryDTO summary = new DashboardSummaryDTO();
        when(dashboardService.getSummary()).thenReturn(summary);

        DashboardController controller = new DashboardController(dashboardService);

        assertEquals(summary, controller.summary().getData());
        verify(dashboardService).getSummary();
    }
}
