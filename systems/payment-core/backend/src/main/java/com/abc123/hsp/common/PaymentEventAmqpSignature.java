package com.abc123.hsp.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class PaymentEventAmqpSignature {

    public static final String HEADER_NAME = "X-HSP-Payment-Event-AMQP-Signature";

    private PaymentEventAmqpSignature() {
    }

    public static String sign(byte[] body, String secret) {
        if (body == null || !StringUtils.hasText(secret)) {
            throw new IllegalStateException("支付事件 AMQP 签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("支付事件 AMQP 签名不可用", exception);
        }
    }

    public static boolean matches(byte[] body, Object signature, String secret) {
        return body != null && StringUtils.hasText(secret) && signature instanceof String
                && StringUtils.hasText((String) signature)
                && MessageDigest.isEqual(sign(body, secret).getBytes(StandardCharsets.US_ASCII),
                        ((String) signature).getBytes(StandardCharsets.US_ASCII));
    }
}
