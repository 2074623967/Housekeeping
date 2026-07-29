package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentMetricsDTO;
import com.abc123.hsp.mapper.PaymentMetricsMapper;
import com.abc123.hsp.service.PaymentMetricsService;
import org.springframework.stereotype.Service;

/**
 * 支付指标服务实现。
 */
@Service
public class PaymentMetricsServiceImpl implements PaymentMetricsService {

    private final PaymentMetricsMapper paymentMetricsMapper;

    public PaymentMetricsServiceImpl(PaymentMetricsMapper paymentMetricsMapper) {
        this.paymentMetricsMapper = paymentMetricsMapper;
    }

    @Override
    public PaymentMetricsDTO summary() {
        return paymentMetricsMapper.findSummary();
    }
}
