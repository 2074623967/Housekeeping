package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentSubmitRateLimitMapper;
import com.abc123.hsp.service.PaymentSubmitRateLimitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于数据库共享固定时间窗的支付提交限流实现。
 */
@Service
public class PaymentSubmitRateLimitServiceImpl implements PaymentSubmitRateLimitService {

    private static final String SCOPE_INTERFACE = "INTERFACE";
    private static final String SCOPE_SOURCE_METHOD = "SOURCE_METHOD";
    private static final int WINDOW_SECONDS = 60;

    private final PaymentSubmitRateLimitMapper mapper;

    public PaymentSubmitRateLimitServiceImpl(PaymentSubmitRateLimitMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = BusinessException.class)
    public void acquire(String sourceAppId,
                        String paymentMethod,
                        String terminal,
                        String clientIp,
                        int interfaceMinuteLimit,
                        int sourceMethodMinuteLimit) {
        acquireScope(
                SCOPE_INTERFACE,
                scopeKey(sourceAppId, terminal, clientIp),
                interfaceMinuteLimit,
                ErrorCode.PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED,
                "当前接口提交过于频繁，请稍后重试");
        acquireScope(
                SCOPE_SOURCE_METHOD,
                scopeKey(sourceAppId, paymentMethod),
                sourceMethodMinuteLimit,
                ErrorCode.PAYMENT_SUBMIT_RATE_LIMITED,
                "当前来源应用支付提交过于频繁，请稍后重试");
    }

    private void acquireScope(String scopeType,
                              String scopeKey,
                              int maxRequests,
                              String errorCode,
                              String errorMessage) {
        if (maxRequests <= 0) {
            return;
        }
        mapper.initBucket(scopeType, scopeKey);
        int affectedRows = mapper.acquire(scopeType, scopeKey, WINDOW_SECONDS, maxRequests);
        if (affectedRows == 0) {
            throw new BusinessException(errorCode, errorMessage);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("payment submit rate limit acquisition must affect exactly one row");
        }
    }

    private String scopeKey(String... dimensions) {
        StringBuilder source = new StringBuilder();
        for (String dimension : dimensions) {
            String value = dimension == null ? "" : dimension.trim().toLowerCase(Locale.ROOT);
            source.append(value.length()).append(':').append(value).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }
}
