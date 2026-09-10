package com.abc123.hsp.service;

/**
 * 退款渠道查单共享限流服务。
 */
public interface RefundChannelQueryRateLimitService {

    /**
     * 为指定退款单的渠道查单占用一个时间窗配额，超过上限时记录拒绝事实并抛出业务异常。
     */
    void acquire(String channelCode, String refundOrderId);
}
