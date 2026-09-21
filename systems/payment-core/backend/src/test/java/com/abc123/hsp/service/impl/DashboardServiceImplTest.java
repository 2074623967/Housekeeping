package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.abc123.hsp.mapper.DashboardMapper;
import com.abc123.hsp.dto.DashboardSummaryDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 工作台服务测试。
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private DashboardMapper dashboardMapper;

    @Test
    void shouldReturnEmptyCardsWhenDashboardQueryHasNoRows() {
        when(dashboardMapper.findCards()).thenReturn(null);

        DashboardSummaryDTO summary = new DashboardServiceImpl(dashboardMapper).getSummary();

        assertNotNull(summary);
        assertNotNull(summary.getCards());
        assertTrue(summary.getCards().isEmpty());
    }
}
