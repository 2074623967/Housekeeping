package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;

/**
 * 支付日终处理服务。
 */
public interface PaymentDayEndService {

    /**
     * 查询支付日终处理总览。
     */
    PaymentDayEndOverviewDTO overview();

    /**
     * 触发支付日终处理。
     */
    PaymentDayEndOverviewDTO run(PaymentDayEndRunRequestDTO request);
}
