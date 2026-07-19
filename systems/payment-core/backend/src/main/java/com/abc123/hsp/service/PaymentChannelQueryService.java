package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentDetailDTO;

/**
 * 支付渠道查单服务。
 */
public interface PaymentChannelQueryService {

    /**
     * 查询指定支付单的渠道状态。
     *
     * <p>当前默认适配器只返回本地模拟状态，真实渠道适配器由 gateway-access
     * 接入后注册到该服务。</p>
     */
    PaymentDetailDTO query(PaymentDetailDTO paymentDetail);
}
