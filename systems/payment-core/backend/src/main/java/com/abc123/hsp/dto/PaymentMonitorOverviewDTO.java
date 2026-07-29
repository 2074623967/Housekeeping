package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付监控分析总览。
 */
@Data
public class PaymentMonitorOverviewDTO {

    /** 监控摘要。 */
    private PaymentMonitorSummaryDTO summary;
    /** 趋势统计。 */
    private List<PaymentTrendPointDTO> trends;
    /** 渠道监控统计。 */
    private List<PaymentChannelMetricDTO> channelMetrics;
    /** 异常告警列表。 */
    private List<PaymentAlertItemDTO> alerts;
}
