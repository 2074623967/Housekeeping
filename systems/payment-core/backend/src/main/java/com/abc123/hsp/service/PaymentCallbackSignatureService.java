package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentCallbackRequestDTO;

/**
 * 支付渠道回调签名校验服务。
 *
 * <p>当前使用 HMAC-SHA256 作为统一接入基线，具体微信、支付宝和银行适配器
 * 后续可以在网关接入系统中替换为各自的验签实现。</p>
 */
public interface PaymentCallbackSignatureService {

    /**
     * 校验渠道回调签名。
     *
     * @param channel 渠道编码
     * @param request 回调请求
     */
    void verify(String channel, PaymentCallbackRequestDTO request);
}
