package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import java.util.List;

/**
 * 支付监控分析 Mapper。
 */
public interface PaymentMonitorMapper {

    /**
     * 查询支付监控摘要。
     */
    PaymentMonitorSummaryDTO findSummary();

    /**
     * 查询最近支付趋势。
     */
    List<PaymentTrendPointDTO> findRecentTrends();

    /**
     * 查询渠道维度监控指标。
     */
    List<PaymentChannelMetricDTO> findChannelMetrics();

    /**
     * 查询待处理异常告警。
     */
    List<PaymentAlertItemDTO> findAlerts();

}
