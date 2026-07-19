package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * HMAC-SHA256 回调签名校验实现。
 */
@Service
public class PaymentCallbackSignatureServiceImpl implements PaymentCallbackSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final boolean required;
    private final String secret;
    private final long allowedSkewSeconds;
    private final long nonceTtlSeconds;
    private final Map<String, Long> nonceExpiryMap = new ConcurrentHashMap<>();

    public PaymentCallbackSignatureServiceImpl(
            @Value("${payment.callback.require-signature:false}") boolean required,
            @Value("${payment.callback.secret:}") String secret,
            @Value("${payment.callback.allowed-skew-seconds:300}") long allowedSkewSeconds,
            @Value("${payment.callback.nonce-ttl-seconds:600}") long nonceTtlSeconds) {
        this.required = required;
        this.secret = secret;
        this.allowedSkewSeconds = allowedSkewSeconds;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    @Override
    public void verify(String channel, PaymentCallbackRequestDTO request) {
        if (!required) {
            return;
        }
        if (!StringUtils.hasText(secret)
                || !StringUtils.hasText(request.getSignature())
                || !StringUtils.hasText(request.getTimestamp())
                || !StringUtils.hasText(request.getNonce())) {
            throw new IllegalArgumentException("callback signature fields are required");
        }
        long timestampSeconds = parseTimestamp(request.getTimestamp());
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > allowedSkewSeconds) {
            throw new IllegalArgumentException("callback timestamp is out of allowed window");
        }
        String payload = String.join("|",
                channel,
                request.getPaymentOrderId(),
                request.getTradeStatus(),
                request.getChannelTransactionNo(),
                request.getTimestamp(),
                request.getNonce());
        String expectedSignature = sign(payload, secret);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                request.getSignature().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("callback signature verification failed");
        }
        String nonceKey = channel + "|" + request.getNonce();
        cleanupExpiredNonce(nowSeconds);
        Long previousExpiry = nonceExpiryMap.putIfAbsent(nonceKey, nowSeconds + nonceTtlSeconds);
        if (previousExpiry != null) {
            throw new IllegalArgumentException("callback nonce replay detected");
        }
    }

    /**
     * 按秒级时间戳解析渠道回调时间。
     */
    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("callback timestamp is invalid", exception);
        }
    }

    /**
     * 清理过期 nonce，避免测试和长时间运行后内存一直增长。
     */
    private void cleanupExpiredNonce(long nowSeconds) {
        nonceExpiryMap.entrySet().removeIf(entry -> entry.getValue() < nowSeconds);
    }

    private String sign(String payload, String signingSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to create callback signature", exception);
        }
    }
}
