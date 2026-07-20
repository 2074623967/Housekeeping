package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;

/**
 * 支付配置中心服务。
 */
public interface PaymentConfigService {

    /**
     * 查询渠道和路由规则配置总览。
     */
    PaymentConfigOverviewDTO overview();

    /**
     * 启停支付渠道。
     */
    PaymentConfigOverviewDTO toggleChannel(PaymentConfigToggleRequestDTO request);

    /**
     * 启停路由规则。
     */
    PaymentConfigOverviewDTO toggleRouteRule(PaymentConfigToggleRequestDTO request);

    /**
     * 启停支付协议配置。
     */
    PaymentConfigOverviewDTO toggleProtocol(PaymentConfigToggleRequestDTO request);
}
