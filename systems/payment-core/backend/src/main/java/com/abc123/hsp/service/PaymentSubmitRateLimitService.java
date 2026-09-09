package com.abc123.hsp.service;

/**
 * 支付提交共享数据库限流服务。
 */
public interface PaymentSubmitRateLimitService {

    /**
     * 原子占用接口范围和来源应用支付方式范围的分钟配额。
     */
    void acquire(String sourceAppId,
                 String paymentMethod,
                 String terminal,
                 String clientIp,
                 int interfaceMinuteLimit,
                 int sourceMethodMinuteLimit);
}
