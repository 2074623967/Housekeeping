package com.abc123.clearing.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class PaymentEventHttpSignature {

    public static final String HEADER_NAME = "X-HSP-Payment-Event-Signature";
    public static final String TIMESTAMP_HEADER_NAME = "X-HSP-Payment-Event-Timestamp";
    private static final long MAX_SKEW_SECONDS = 300;

    private PaymentEventHttpSignature() {
    }

    public static String sign(String body, String secret) {
        if (body == null || !StringUtils.hasText(secret)) {
            throw new IllegalStateException("支付事件 HTTP 签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付事件 HTTP 签名不可用", exception);
        }
    }

    public static boolean matches(String body, String signature, String secret) {
        return body != null && StringUtils.hasText(secret) && StringUtils.hasText(signature)
                && MessageDigest.isEqual(sign(body, secret).getBytes(StandardCharsets.US_ASCII),
                        signature.getBytes(StandardCharsets.US_ASCII));
    }

    public static String signTimed(String body, String timestamp, String secret) {
        if (body == null || !StringUtils.hasText(timestamp)) {
            throw new IllegalArgumentException("支付事件时间戳或正文无效");
        }
        return sign(timestamp + "\n" + body, secret);
    }

    public static boolean matchesTimed(String body, String timestamp, String signature, String secret) {
        if (body == null || !StringUtils.hasText(secret) || !StringUtils.hasText(signature)
                || timestamp == null || !timestamp.matches("[0-9]{1,19}")) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            return Long.toString(seconds).equals(timestamp)
                    && seconds >= now - MAX_SKEW_SECONDS && seconds <= now + MAX_SKEW_SECONDS
                    && MessageDigest.isEqual(signTimed(body, timestamp, secret).getBytes(StandardCharsets.US_ASCII),
                            signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
