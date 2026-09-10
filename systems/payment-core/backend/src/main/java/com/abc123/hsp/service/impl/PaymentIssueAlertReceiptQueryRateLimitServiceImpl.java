package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentIssueAlertReceiptQueryRateLimitMapper;
import com.abc123.hsp.service.PaymentIssueAlertReceiptQueryRateLimitService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 基于数据库共享固定时间窗的告警供应商回执查询限流实现。
 */
@Service
public class PaymentIssueAlertReceiptQueryRateLimitServiceImpl
        implements PaymentIssueAlertReceiptQueryRateLimitService {

    private final PaymentIssueAlertReceiptQueryRateLimitMapper mapper;
    private final boolean enabled;
    private final int windowSeconds;
    private final int maxQueries;

    @Autowired
    public PaymentIssueAlertReceiptQueryRateLimitServiceImpl(
            PaymentIssueAlertReceiptQueryRateLimitMapper mapper,
            @Value("${payment.issue-alert.receipt-query.rate-limit.enabled:false}") boolean enabled,
            @Value("${payment.issue-alert.receipt-query.gateway-access.enabled:false}") boolean gatewayQueryEnabled,
            @Value("${payment.issue-alert.receipt-query.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${payment.issue-alert.receipt-query.rate-limit.max-queries-per-channel:100}") int maxQueries) {
        this(mapper, enabled && gatewayQueryEnabled, windowSeconds, maxQueries);
    }

    PaymentIssueAlertReceiptQueryRateLimitServiceImpl(
            PaymentIssueAlertReceiptQueryRateLimitMapper mapper,
            boolean enabled,
            int windowSeconds,
            int maxQueries) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.windowSeconds = Math.max(windowSeconds, 1);
        this.maxQueries = Math.max(maxQueries, 1);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BusinessException.class)
    public void acquire(String channelCode, String alertNo) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(channelCode)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMITED,
                    "alert channel is required for receipt query rate limiting");
        }
        String normalizedChannelCode = channelCode.trim().toUpperCase(Locale.ROOT);
        mapper.initBucket(normalizedChannelCode);
        if (mapper.acquire(normalizedChannelCode, windowSeconds, maxQueries) == 0) {
            mapper.insertRejection(
                    "IARQRL" + UUID.randomUUID().toString().replace("-", ""),
                    normalizedChannelCode,
                    alertNo,
                    windowSeconds,
                    maxQueries);
            throw new BusinessException(
                    ErrorCode.PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMITED,
                    String.format("alert receipt query rate limit exceeded: %d requests per %d seconds",
                            maxQueries, windowSeconds));
        }
    }
}
