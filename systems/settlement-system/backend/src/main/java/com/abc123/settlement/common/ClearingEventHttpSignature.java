package com.abc123.settlement.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class ClearingEventHttpSignature {

    public static final String HEADER_NAME = "X-HSP-Clearing-Event-Signature";

    private ClearingEventHttpSignature() {
    }

    public static String sign(String body, String secret) {
        if (body == null || !StringUtils.hasText(secret)) {
            throw new IllegalStateException("清分事件 HTTP 签名密钥未配置");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("清分事件 HTTP 签名不可用", exception);
        }
    }

    public static boolean matches(String body, String signature, String secret) {
        return body != null && StringUtils.hasText(secret) && StringUtils.hasText(signature)
                && MessageDigest.isEqual(sign(body, secret).getBytes(StandardCharsets.US_ASCII),
                        signature.getBytes(StandardCharsets.US_ASCII));
    }
}
