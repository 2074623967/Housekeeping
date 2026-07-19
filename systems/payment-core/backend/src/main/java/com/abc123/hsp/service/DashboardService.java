package com.abc123.hsp.service;

import com.abc123.hsp.dto.DashboardSummaryDTO;
import com.abc123.hsp.repository.DashboardRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    public DashboardSummaryDTO getSummary() {
        return new DashboardSummaryDTO(dashboardRepository.findCards());
    }
}
