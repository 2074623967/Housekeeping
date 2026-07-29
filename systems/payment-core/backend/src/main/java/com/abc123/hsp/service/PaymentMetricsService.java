package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentMetricsDTO;

/**
 * 支付指标服务。
 */
public interface PaymentMetricsService {

    /**
     * 查询实时支付指标。
     */
    PaymentMetricsDTO summary();
}
