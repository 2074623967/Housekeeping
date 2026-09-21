package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import com.abc123.hsp.mapper.PaymentMonitorMapper;
import com.abc123.hsp.service.PaymentMonitorService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付监控分析服务实现。
 */
@Service
public class PaymentMonitorServiceImpl implements PaymentMonitorService {

    private final PaymentMonitorMapper paymentMonitorMapper;

    public PaymentMonitorServiceImpl(PaymentMonitorMapper paymentMonitorMapper) {
        this.paymentMonitorMapper = paymentMonitorMapper;
    }

    @Override
    public PaymentMonitorOverviewDTO overview() {
        PaymentMonitorOverviewDTO overview = new PaymentMonitorOverviewDTO();
        PaymentMonitorSummaryDTO summary = paymentMonitorMapper.findSummary();
        if (summary == null) {
            summary = new PaymentMonitorSummaryDTO();
        }
        overview.setTrends(safeList(paymentMonitorMapper.findRecentTrends()));
        overview.setChannelMetrics(safeList(paymentMonitorMapper.findChannelMetrics()));
        overview.setAlerts(safeList(paymentMonitorMapper.findAlerts()));
        summary.setAlertCount(overview.getAlerts().size());
        overview.setSummary(summary);
        return overview;
    }

    @Override
    public String exportCsv() {
        PaymentMonitorOverviewDTO overview = overview();
        StringBuilder csv = new StringBuilder();
        csv.append("section,metric,value,detail\n");
        PaymentMonitorSummaryDTO summary = overview.getSummary();
        appendRow(csv, "summary", "totalCount", summary.getTotalCount(), "");
        appendRow(csv, "summary", "successCount", summary.getSuccessCount(), "");
        appendRow(csv, "summary", "successRate", summary.getSuccessRate(), "");
        appendRow(csv, "summary", "successAmount", summary.getSuccessAmount(), "");
        appendRow(csv, "summary", "pendingPaymentCount", summary.getPendingPaymentCount(), "");
        appendRow(csv, "summary", "closedPaymentCount", summary.getClosedPaymentCount(), "");
        appendRow(csv, "summary", "failedRefundCount", summary.getFailedRefundCount(), "");
        appendRow(csv, "summary", "disabledChannelCount", summary.getDisabledChannelCount(), "");
        appendRow(csv, "summary", "alertCount", summary.getAlertCount(), "");
        for (PaymentTrendPointDTO trend : safeList(overview.getTrends())) {
            appendRow(csv, "trend", trend.getStatDate(), trend.getTotalCount(),
                    "successCount=" + value(trend.getSuccessCount()) + ";successAmount=" + value(trend.getSuccessAmount()));
        }
        for (PaymentChannelMetricDTO channel : safeList(overview.getChannelMetrics())) {
            appendRow(csv, "channel", channel.getChannelCode(), channel.getSuccessRate(),
                    "paymentMethod=" + value(channel.getPaymentMethod()) + ";totalCount=" + value(channel.getTotalCount())
                            + ";successCount=" + value(channel.getSuccessCount()) + ";pendingCount=" + value(channel.getPendingCount())
                            + ";riskLevel=" + value(channel.getRiskLevel()));
        }
        for (PaymentAlertItemDTO alert : safeList(overview.getAlerts())) {
            appendRow(csv, "alert", alert.getAlertType(), alert.getAlertLevel(),
                    "title=" + value(alert.getAlertTitle()) + ";affectedCount=" + value(alert.getAffectedCount())
                            + ";suggestedAction=" + value(alert.getSuggestedAction()));
        }
        return csv.toString();
    }

    private void appendRow(StringBuilder csv, String section, Object metric, Object value, String detail) {
        csv.append(csvValue(section)).append(',')
                .append(csvValue(metric)).append(',')
                .append(csvValue(value)).append(',')
                .append(csvValue(detail)).append('\n');
    }

    private String csvValue(Object value) {
        String normalized = CsvExportSanitizer.preventFormula(value(value)).replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? java.util.Collections.<T>emptyList() : values;
    }
}
