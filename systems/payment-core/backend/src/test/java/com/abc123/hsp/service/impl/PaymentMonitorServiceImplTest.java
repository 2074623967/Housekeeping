package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import com.abc123.hsp.mapper.PaymentMonitorMapper;
import java.util.Collections;
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
        when(paymentMonitorMapper.findSummary()).thenReturn(new PaymentMonitorSummaryDTO());
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(Collections.<PaymentTrendPointDTO>emptyList());
        when(paymentMonitorMapper.findChannelMetrics()).thenReturn(Collections.<PaymentChannelMetricDTO>emptyList());
        when(paymentMonitorMapper.findAlerts()).thenReturn(Collections.<PaymentAlertItemDTO>emptyList());

        new PaymentMonitorServiceImpl(paymentMonitorMapper).overview();

        verify(paymentMonitorMapper).findSummary();
        verify(paymentMonitorMapper).findRecentTrends();
        verify(paymentMonitorMapper).findChannelMetrics();
        verify(paymentMonitorMapper).findAlerts();
    }
}
