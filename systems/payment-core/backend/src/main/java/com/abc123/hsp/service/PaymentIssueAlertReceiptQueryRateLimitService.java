package com.abc123.hsp.service;

/**
 * 告警供应商回执查询共享限流服务。
 */
public interface PaymentIssueAlertReceiptQueryRateLimitService {

    /** 在调用通知集成网关前占用目标通道的查询额度。 */
    void acquire(String channelCode, String alertNo);
}
