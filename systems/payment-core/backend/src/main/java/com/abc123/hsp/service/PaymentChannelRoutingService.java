package com.abc123.hsp.service;

/**
 * 支付渠道路由服务。
 */
public interface PaymentChannelRoutingService {

    /**
     * 根据支付方式和请求渠道解析标准渠道编码。
     *
     * @param paymentMethod 支付方式
     * @param requestedChannelCode 调用方请求的渠道编码
     * @return 标准渠道编码
     */
    String resolve(String paymentMethod, String requestedChannelCode);
}
