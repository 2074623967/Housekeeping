package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.mapper.PaymentMonitorMapper;
import com.abc123.hsp.service.PaymentMonitorService;
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
        overview.setTrends(paymentMonitorMapper.findRecentTrends());
        overview.setChannelMetrics(paymentMonitorMapper.findChannelMetrics());
        overview.setAlerts(paymentMonitorMapper.findAlerts());
        summary.setAlertCount(overview.getAlerts().size());
        overview.setSummary(summary);
        return overview;
    }
}
