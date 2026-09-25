package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.DashboardSummaryDTO;
import com.abc123.hsp.dto.DashboardCardDTO;
import com.abc123.hsp.mapper.DashboardMapper;
import com.abc123.hsp.service.DashboardService;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DashboardMapper dashboardMapper;

    public DashboardServiceImpl(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public DashboardSummaryDTO getSummary() {
        List<DashboardCardDTO> cards = dashboardMapper.findCards();
        if (cards == null || cards.isEmpty()) {
            return new DashboardSummaryDTO(Collections.emptyList());
        }
        List<DashboardCardDTO> safeCards = new ArrayList<>(cards.size());
        for (DashboardCardDTO card : cards) {
            if (card != null) {
                safeCards.add(card);
            }
        }
        return new DashboardSummaryDTO(safeCards);
    }
}
