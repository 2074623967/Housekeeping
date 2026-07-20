package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.mapper.PaymentCallbackSecurityMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * HMAC-SHA256 回调签名校验实现。
 */
@Service
public class PaymentCallbackSignatureServiceImpl implements PaymentCallbackSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final boolean required;
    private final String fallbackSecret;
    private final long allowedSkewSeconds;
    private final long nonceTtlSeconds;
    private final PaymentCallbackSecurityMapper paymentCallbackSecurityMapper;

    public PaymentCallbackSignatureServiceImpl(
            PaymentCallbackSecurityMapper paymentCallbackSecurityMapper,
            @Value("${payment.callback.require-signature:false}") boolean required,
            @Value("${payment.callback.secret:}") String secret,
            @Value("${payment.callback.allowed-skew-seconds:300}") long allowedSkewSeconds,
            @Value("${payment.callback.nonce-ttl-seconds:600}") long nonceTtlSeconds) {
        this.paymentCallbackSecurityMapper = paymentCallbackSecurityMapper;
        this.required = required;
        this.fallbackSecret = secret;
        this.allowedSkewSeconds = allowedSkewSeconds;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    @Override
    public void verify(String channel, PaymentCallbackRequestDTO request) {
        if (!required) {
            return;
        }
        if (!StringUtils.hasText(request.getSignature())
                || !StringUtils.hasText(request.getTimestamp())
                || !StringUtils.hasText(request.getNonce())) {
            throw new IllegalArgumentException("callback signature fields are required");
        }
        String normalizedChannel = normalizeChannel(channel);
        String callbackSecret = resolveCallbackSecret(normalizedChannel);
        long timestampSeconds = parseTimestamp(request.getTimestamp());
        long nowSeconds = Instant.now().getEpochSecond();
        if (Math.abs(nowSeconds - timestampSeconds) > allowedSkewSeconds) {
            throw new IllegalArgumentException("callback timestamp is out of allowed window");
        }
        String payload = String.join("|",
                normalizedChannel,
                request.getPaymentOrderId(),
                request.getTradeStatus(),
                request.getChannelTransactionNo(),
                request.getTimestamp(),
                request.getNonce());
        String expectedSignature = sign(payload, callbackSecret);
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                request.getSignature().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("callback signature verification failed");
        }
        paymentCallbackSecurityMapper.deleteExpiredNonce();
        try {
            paymentCallbackSecurityMapper.insertCallbackNonce(
                    normalizedChannel,
                    request.getNonce(),
                    request.getPaymentOrderId(),
                    nonceTtlSeconds
            );
        } catch (DuplicateKeyException exception) {
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

    private String resolveCallbackSecret(String normalizedChannel) {
        String callbackSecret = paymentCallbackSecurityMapper.findCallbackSecretByChannelCode(normalizedChannel);
        if (StringUtils.hasText(callbackSecret)) {
            return callbackSecret;
        }
        if (StringUtils.hasText(fallbackSecret)) {
            return fallbackSecret;
        }
        throw new IllegalArgumentException("callback secret is not configured");
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            throw new IllegalArgumentException("callback channel is required");
        }
        return channel.trim().toLowerCase();
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
