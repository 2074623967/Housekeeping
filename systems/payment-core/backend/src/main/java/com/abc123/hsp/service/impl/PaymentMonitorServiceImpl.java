package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.dto.PaymentMonitorMetricDefinitionDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import com.abc123.hsp.mapper.PaymentMonitorMapper;
import com.abc123.hsp.service.PaymentMonitorService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
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
        if (summary.getProcessingRefundCount() == null) {
            summary.setProcessingRefundCount(0);
        }
        overview.setTrends(safeList(paymentMonitorMapper.findRecentTrends()));
        overview.setChannelMetrics(safeList(paymentMonitorMapper.findChannelMetrics()));
        overview.setAlerts(safeList(paymentMonitorMapper.findAlerts()));
        overview.setMetricDefinitions(metricDefinitions());
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
        appendRow(csv, "summary", "processingRefundCount", summary.getProcessingRefundCount(), "");
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
        for (PaymentMonitorMetricDefinitionDTO definition : safeList(overview.getMetricDefinitions())) {
            appendRow(csv, "metric-definition", definition.getMetricCode(), definition.getDefaultThreshold(),
                    "name=" + value(definition.getMetricName()) + ";unit=" + value(definition.getUnit())
                            + ";alertLevel=" + value(definition.getAlertLevel()) + ";owner=" + value(definition.getOwner())
                            + ";actionRoute=" + value(definition.getActionRoute()));
        }
        return csv.toString();
    }

    private List<PaymentMonitorMetricDefinitionDTO> metricDefinitions() {
        return Arrays.asList(
                metricDefinition("payment_success_rate", "支付成功率", "成功支付单量 / 支付单总量", "%", ">= 95%", "P1", "支付运营", "/payments"),
                metricDefinition("pending_payment_count", "待收口支付", "已提交渠道但尚未确认最终结果的支付单量", "笔", "= 0", "P1", "支付运营", "/payments?status=WAIT_CALLBACK"),
                metricDefinition("failed_refund_count", "失败退款", "退款处理失败且仍需人工复核或重试的退款单量", "笔", "= 0", "P1", "退款运营", "/refunds?refundStatus=FAIL"),
                metricDefinition("disabled_channel_count", "停用渠道", "当前不应继续承接支付请求的停用渠道数量", "个", "= 0", "P2", "支付配置运营", "/payment-config?channelStatus=DISABLED"),
                metricDefinition("payment_alert_p1_count", "P1 告警", "当前监控窗口内仍未完成处置的 P1 告警数量", "条", "= 0", "P1", "支付值班", "/payment-issues")
        );
    }

    private PaymentMonitorMetricDefinitionDTO metricDefinition(String metricCode, String metricName, String definition,
            String unit, String defaultThreshold, String alertLevel, String owner, String actionRoute) {
        PaymentMonitorMetricDefinitionDTO item = new PaymentMonitorMetricDefinitionDTO();
        item.setMetricCode(metricCode);
        item.setMetricName(metricName);
        item.setDefinition(definition);
        item.setUnit(unit);
        item.setDefaultThreshold(defaultThreshold);
        item.setAlertLevel(alertLevel);
        item.setOwner(owner);
        item.setActionRoute(actionRoute);
        return item;
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
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }
}
