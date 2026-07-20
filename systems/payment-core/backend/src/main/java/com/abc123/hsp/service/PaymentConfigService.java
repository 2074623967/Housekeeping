package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;

/**
 * 支付配置中心服务。
 */
public interface PaymentConfigService {

    /**
     * 查询支付配置中心总览。
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

    /**
     * 新增支付协议配置。
     */
    PaymentConfigOverviewDTO createProtocol(PaymentProtocolUpsertRequestDTO request);

    /**
     * 编辑支付协议配置。
     */
    PaymentConfigOverviewDTO updateProtocol(String protocolCode, PaymentProtocolUpsertRequestDTO request);

    /**
     * 启停渠道返回码映射配置。
     */
    PaymentConfigOverviewDTO toggleReturnCodeMapping(PaymentConfigToggleRequestDTO request);

    /**
     * 启停支付网关接入配置。
     */
    PaymentConfigOverviewDTO toggleGateway(PaymentConfigToggleRequestDTO request);
}
