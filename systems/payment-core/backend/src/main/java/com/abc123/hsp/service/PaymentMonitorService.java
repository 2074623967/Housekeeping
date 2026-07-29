package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;

/**
 * 支付监控分析服务。
 */
public interface PaymentMonitorService {

    /**
     * 查询支付监控分析总览。
     */
    PaymentMonitorOverviewDTO overview();
}
