package com.abc123.hsp.service;

/**
 * 支付渠道查单共享限流服务。
 */
public interface PaymentChannelQueryRateLimitService {

    /**
     * 为指定渠道占用一个查单时间窗配额，超过上限时抛出业务异常。
     */
    void acquire(String channelCode);
}
