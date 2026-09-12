package com.abc123.hsp.service.impl;

import com.abc123.hsp.mapper.PaymentIssueAlertDeliveryRateLimitMapper;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryRateLimitService;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 基于数据库共享固定时间窗的告警供应商派发限流实现。
 */
@Service
public class PaymentIssueAlertDeliveryRateLimitServiceImpl
        implements PaymentIssueAlertDeliveryRateLimitService {

    private final PaymentIssueAlertDeliveryRateLimitMapper mapper;

    public PaymentIssueAlertDeliveryRateLimitServiceImpl(PaymentIssueAlertDeliveryRateLimitMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquire(String providerCode,
                              String channelCode,
                              int windowSeconds,
                              int maxDeliveries) {
        if (!StringUtils.hasText(providerCode) || !StringUtils.hasText(channelCode)) {
            return false;
        }
        String normalizedProviderCode = providerCode.trim().toUpperCase(Locale.ROOT);
        String normalizedChannelCode = channelCode.trim().toUpperCase(Locale.ROOT);
        int normalizedWindowSeconds = Math.max(windowSeconds, 1);
        int normalizedMaxDeliveries = Math.max(maxDeliveries, 1);
        mapper.initBucket(normalizedProviderCode, normalizedChannelCode);
        return mapper.acquire(
                normalizedProviderCode,
                normalizedChannelCode,
                normalizedWindowSeconds,
                normalizedMaxDeliveries) == 1;
    }
}
