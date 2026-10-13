package com.abc123.refund.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class RefundCallbackHttpSignature {

    public static final String TIMESTAMP_HEADER = "X-HSP-Refund-Callback-Timestamp";
    public static final String SIGNATURE_HEADER = "X-HSP-Refund-Callback-Signature";
    private static final long MAX_SKEW_SECONDS = 300;

    private RefundCallbackHttpSignature() {
    }

    public static String sign(String refundOrderId, String body, String timestamp, String secret) {
        if (refundOrderId == null || body == null || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(secret)) {
            throw new IllegalStateException("退款回调签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(
                    (timestamp + "\n" + refundOrderId + "\n" + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("退款回调签名不可用", exception);
        }
    }

    public static boolean matches(String refundOrderId, String body, String timestamp, String signature,
            String secret) {
        if (refundOrderId == null || body == null || !StringUtils.hasText(secret)
                || !StringUtils.hasText(signature) || timestamp == null
                || !timestamp.matches("[0-9]{1,19}")) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            return Long.toString(seconds).equals(timestamp)
                    && seconds >= now - MAX_SKEW_SECONDS && seconds <= now + MAX_SKEW_SECONDS
                    && MessageDigest.isEqual(sign(refundOrderId, body, timestamp, secret).getBytes(StandardCharsets.US_ASCII),
                            signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
