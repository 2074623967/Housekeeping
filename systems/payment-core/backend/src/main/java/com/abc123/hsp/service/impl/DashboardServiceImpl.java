package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.DashboardSummaryDTO;
import com.abc123.hsp.mapper.DashboardMapper;
import com.abc123.hsp.service.DashboardService;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public DashboardSummaryDTO getSummary() {
        return new DashboardSummaryDTO(dashboardMapper.findCards());
    }
}
