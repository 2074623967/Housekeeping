package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.abc123.hsp.mapper.DashboardMapper;
import com.abc123.hsp.dto.DashboardCardDTO;
import com.abc123.hsp.dto.DashboardSummaryDTO;
import java.util.Arrays;
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

    @Test
    void shouldIgnoreNullDashboardRows() {
        DashboardCardDTO card = new DashboardCardDTO();
        card.setTitle("待处理支付");
        when(dashboardMapper.findCards()).thenReturn(Arrays.asList(null, card));

        DashboardSummaryDTO summary = new DashboardServiceImpl(dashboardMapper).getSummary();

        assertEquals(1, summary.getCards().size());
        assertEquals("待处理支付", summary.getCards().get(0).getTitle());
    }
}
