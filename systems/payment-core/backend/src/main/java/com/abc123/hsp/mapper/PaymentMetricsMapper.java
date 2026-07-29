package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentMetricsDTO;

/**
 * 支付指标查询 Mapper。
 */
public interface PaymentMetricsMapper {

    /**
     * 查询支付订单健康指标。
     */
    PaymentMetricsDTO findSummary();
}
