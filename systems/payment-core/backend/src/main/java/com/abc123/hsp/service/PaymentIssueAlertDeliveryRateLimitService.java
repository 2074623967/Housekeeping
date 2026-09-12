package com.abc123.hsp.service;

/**
 * 告警供应商派发共享限流服务。
 */
@FunctionalInterface
public interface PaymentIssueAlertDeliveryRateLimitService {

    /** 在调用供应商前原子占用一个固定时间窗派发额度。 */
    boolean tryAcquire(String providerCode, String channelCode, int windowSeconds, int maxDeliveries);
}
