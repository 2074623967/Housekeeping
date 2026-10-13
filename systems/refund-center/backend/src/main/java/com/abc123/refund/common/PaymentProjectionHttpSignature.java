package com.abc123.refund.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class PaymentProjectionHttpSignature {

    public static final String TIMESTAMP_HEADER = "X-HSP-Payment-Projection-Timestamp";
    public static final String SIGNATURE_HEADER = "X-HSP-Payment-Projection-Signature";
    private static final long MAX_SKEW_SECONDS = 300;

    private PaymentProjectionHttpSignature() {
    }

    public static String sign(String body, String timestamp, String secret) {
        if (body == null || !StringUtils.hasText(timestamp) || !StringUtils.hasText(secret)) {
            throw new IllegalStateException("支付成功投影签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付成功投影签名不可用", exception);
        }
    }

    public static boolean matches(String body, String timestamp, String signature, String secret) {
        if (body == null || !StringUtils.hasText(secret) || !StringUtils.hasText(signature)
                || timestamp == null || !timestamp.matches("[0-9]{1,19}")) {
            return false;
        }
        try {
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            return Long.toString(seconds).equals(timestamp)
                    && seconds >= now - MAX_SKEW_SECONDS && seconds <= now + MAX_SKEW_SECONDS
                    && MessageDigest.isEqual(sign(body, timestamp, secret).getBytes(StandardCharsets.US_ASCII),
                            signature.getBytes(StandardCharsets.US_ASCII));
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
