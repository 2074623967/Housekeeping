package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.dto.SettlementDashboardDTO;
import com.abc123.settlement.service.SettlementDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算工作台控制器。
 */
@RestController
@RequestMapping("/api/settlements/dashboard")
public class SettlementDashboardController {

    private final SettlementDashboardService settlementDashboardService;

    public SettlementDashboardController(SettlementDashboardService settlementDashboardService) {
        this.settlementDashboardService = settlementDashboardService;
    }

    @GetMapping
    public ApiResponse<SettlementDashboardDTO> dashboard() {
        return ApiResponse.success(settlementDashboardService.dashboard());
    }
}
