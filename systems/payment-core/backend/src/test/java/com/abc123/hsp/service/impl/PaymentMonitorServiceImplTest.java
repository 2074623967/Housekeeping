package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        summary.setProcessingRefundCount(4);
        when(paymentMonitorMapper.findSummary()).thenReturn(summary);
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(Collections.<PaymentTrendPointDTO>emptyList());
        PaymentChannelMetricDTO channel = new PaymentChannelMetricDTO();
        channel.setChannelCode("WECHAT");
        channel.setRiskLevel("P1");
        channel.setRiskLevelType("danger");
        when(paymentMonitorMapper.findChannelMetrics()).thenReturn(Collections.singletonList(channel));
        when(paymentMonitorMapper.findAlerts()).thenReturn(Arrays.asList(
                new PaymentAlertItemDTO(),
                new PaymentAlertItemDTO()));

        PaymentMonitorOverviewDTO overview = new PaymentMonitorServiceImpl(paymentMonitorMapper).overview();

        assertEquals(Integer.valueOf(2), overview.getSummary().getAlertCount());
        assertEquals(Integer.valueOf(4), overview.getSummary().getProcessingRefundCount());
        assertEquals(5, overview.getMetricDefinitions().size());
        assertEquals("payment_success_rate", overview.getMetricDefinitions().get(0).getMetricCode());
        verify(paymentMonitorMapper).findSummary();
        verify(paymentMonitorMapper).findRecentTrends();
        verify(paymentMonitorMapper).findChannelMetrics();
        verify(paymentMonitorMapper).findAlerts();
    }

    @Test
    void shouldExportCurrentMonitorSnapshotWithoutFormulaValues() {
        PaymentMonitorSummaryDTO summary = new PaymentMonitorSummaryDTO();
        summary.setTotalCount(3);
        summary.setSuccessRate("=1+1");
        when(paymentMonitorMapper.findSummary()).thenReturn(summary);
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(Collections.<PaymentTrendPointDTO>emptyList());
        when(paymentMonitorMapper.findChannelMetricsForExport())
                .thenReturn(Collections.<PaymentChannelMetricDTO>emptyList());
        PaymentAlertItemDTO alert = new PaymentAlertItemDTO();
        alert.setAlertType("PAYMENT_FAILURE_RATE");
        alert.setAlertLevel("P1");
        alert.setAlertLevelType("danger");
        when(paymentMonitorMapper.findAlerts()).thenReturn(Collections.singletonList(alert));

        String csv = new PaymentMonitorServiceImpl(paymentMonitorMapper).exportCsv();

        assertEquals(true, csv.startsWith("section,metric,value,detail\n"));
        assertEquals(true, csv.contains("\"successRate\",\"'=1+1\""));
        assertTrue(csv.contains("alertLevel=P1"));
        assertTrue(csv.contains("alertLevelType=danger"));
        assertTrue(csv.contains("\"metric-definition\",\"payment_success_rate\""));
        verify(paymentMonitorMapper).findSummary();
    }

    @Test
    void shouldAllowMonitorCsvAtCombinedRowLimit() {
        when(paymentMonitorMapper.findChannelMetricsForExport()).thenReturn(
                Collections.nCopies(985, new PaymentChannelMetricDTO()));

        String csv = new PaymentMonitorServiceImpl(paymentMonitorMapper).exportCsv();

        assertEquals(1001, csv.split("\n").length);
    }

    @Test
    void shouldRejectMonitorCsvAboveCombinedRowLimit() {
        when(paymentMonitorMapper.findChannelMetricsForExport()).thenReturn(
                Collections.nCopies(986, new PaymentChannelMetricDTO()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PaymentMonitorServiceImpl(paymentMonitorMapper).exportCsv());

        assertEquals("导出数据超过1000条，请缩小筛选范围", exception.getMessage());
    }

    @Test
    void shouldReturnEmptyCollectionsWhenMonitorSourcesHaveNoRows() {
        when(paymentMonitorMapper.findSummary()).thenReturn(null);
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(null);
        when(paymentMonitorMapper.findChannelMetrics()).thenReturn(null);
        when(paymentMonitorMapper.findAlerts()).thenReturn(null);

        PaymentMonitorOverviewDTO overview = new PaymentMonitorServiceImpl(paymentMonitorMapper).overview();

        assertEquals(Integer.valueOf(0), overview.getSummary().getAlertCount());
        assertEquals(Integer.valueOf(0), overview.getSummary().getProcessingRefundCount());
        assertEquals(0, overview.getTrends().size());
        assertEquals(0, overview.getChannelMetrics().size());
        assertEquals(0, overview.getAlerts().size());
    }

    @Test
    void shouldIgnoreNullRowsInMonitorSources() {
        PaymentMonitorSummaryDTO summary = new PaymentMonitorSummaryDTO();
        when(paymentMonitorMapper.findSummary()).thenReturn(summary);
        when(paymentMonitorMapper.findRecentTrends()).thenReturn(Arrays.asList(null, new PaymentTrendPointDTO()));
        when(paymentMonitorMapper.findChannelMetrics()).thenReturn(Arrays.asList(null, new PaymentChannelMetricDTO()));
        when(paymentMonitorMapper.findAlerts()).thenReturn(Arrays.asList(null, new PaymentAlertItemDTO()));

        PaymentMonitorOverviewDTO overview = new PaymentMonitorServiceImpl(paymentMonitorMapper).overview();

        assertEquals(1, overview.getTrends().size());
        assertEquals(1, overview.getChannelMetrics().size());
        assertEquals(1, overview.getAlerts().size());
        assertEquals(Integer.valueOf(1), overview.getSummary().getAlertCount());
    }
}
