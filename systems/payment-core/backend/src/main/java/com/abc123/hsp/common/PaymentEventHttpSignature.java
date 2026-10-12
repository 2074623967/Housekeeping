package com.abc123.hsp.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class PaymentEventHttpSignature {

    public static final String HEADER_NAME = "X-HSP-Payment-Event-Signature";

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
}
