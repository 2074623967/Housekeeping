package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.DashboardSummaryDTO;
import com.abc123.hsp.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 查询支付工作台摘要指标。
     *
     * @return 工作台摘要数据
     */
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryDTO> summary() {
        return ApiResponse.success(dashboardService.getSummary());
    }
}
