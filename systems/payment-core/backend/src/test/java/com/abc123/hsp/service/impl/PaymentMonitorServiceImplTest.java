package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import com.abc123.hsp.mapper.PaymentMonitorMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付监控分析服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentMonitorServiceImplTest {

    @Mock
    private PaymentMonitorMapper paymentMonitorMapper;

    @Test
    void shouldLoadMonitorOverview() {
        PaymentMonitorSummaryDTO summary = new PaymentMonitorSummaryDTO();
        summary.setAlertCount(99);
        when(paymentMonitorMapper.findSummary()).thenReturn(summary);
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(Collections.<PaymentTrendPointDTO>emptyList());
        when(paymentMonitorMapper.findChannelMetrics()).thenReturn(Collections.<PaymentChannelMetricDTO>emptyList());
        when(paymentMonitorMapper.findAlerts()).thenReturn(Arrays.asList(
                new PaymentAlertItemDTO(),
                new PaymentAlertItemDTO()));

        PaymentMonitorOverviewDTO overview = new PaymentMonitorServiceImpl(paymentMonitorMapper).overview();

        assertEquals(Integer.valueOf(2), overview.getSummary().getAlertCount());
        verify(paymentMonitorMapper).findSummary();
        verify(paymentMonitorMapper).findRecentTrends();
        verify(paymentMonitorMapper).findChannelMetrics();
        verify(paymentMonitorMapper).findAlerts();
    }
}
