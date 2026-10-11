package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.RefundChannelQueryRateLimitMapper;
import com.abc123.hsp.service.RefundChannelQueryRateLimitService;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 基于数据库共享固定时间窗的退款渠道查单限流实现。
 */
@Service
public class RefundChannelQueryRateLimitServiceImpl implements RefundChannelQueryRateLimitService {

    private final RefundChannelQueryRateLimitMapper mapper;
    private final boolean enabled;
    private final int windowSeconds;
    private final int maxQueries;

    @Autowired
    public RefundChannelQueryRateLimitServiceImpl(
            RefundChannelQueryRateLimitMapper mapper,
            @Value("${payment.refund-query.rate-limit.enabled:false}") boolean enabled,
            @Value("${payment.refund-query.gateway-access.enabled:false}") boolean gatewayQueryEnabled,
            @Value("${payment.refund-query.rate-limit.window-seconds:60}") int windowSeconds,
            @Value("${payment.refund-query.rate-limit.max-queries-per-channel:100}") int maxQueries) {
        this(mapper, enabled && gatewayQueryEnabled, windowSeconds, maxQueries);
    }

    RefundChannelQueryRateLimitServiceImpl(
            RefundChannelQueryRateLimitMapper mapper,
            boolean enabled,
            int windowSeconds,
            int maxQueries) {
        this.mapper = mapper;
        this.enabled = enabled;
        this.windowSeconds = Math.max(windowSeconds, 1);
        this.maxQueries = Math.max(maxQueries, 1);
    }

    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class)
    public void acquire(String channelCode, String refundOrderId) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(channelCode)) {
            throw new BusinessException(
                    ErrorCode.REFUND_CHANNEL_QUERY_FAILED,
                    "refund channel is required for query rate limiting");
        }
        String normalizedChannelCode = channelCode.trim().toLowerCase(Locale.ROOT);
        mapper.initBucket(normalizedChannelCode);
        int affectedRows = mapper.acquire(normalizedChannelCode, windowSeconds, maxQueries);
        if (affectedRows == 0) {
            if (mapper.insertRejection(
                    "RQRL" + UUID.randomUUID().toString().replace("-", ""),
                    normalizedChannelCode,
                    refundOrderId,
                    windowSeconds,
                    maxQueries) != 1) {
                throw new BusinessException(
                        ErrorCode.REFUND_CHANNEL_QUERY_RATE_LIMITED,
                        "refund channel query rate limit rejection fact could not be recorded");
            }
            throw new BusinessException(
                    ErrorCode.REFUND_CHANNEL_QUERY_RATE_LIMITED,
                    String.format("refund channel query rate limit exceeded: %d requests per %d seconds",
                            maxQueries, windowSeconds));
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("refund channel query rate limit acquisition must affect exactly one row");
        }
    }
}
