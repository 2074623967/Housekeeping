package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
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

    public PaymentCallbackSignatureServiceImpl(
            @Value("${payment.callback.require-signature:false}") boolean required,
            @Value("${payment.callback.secret:}") String secret) {
        this.required = required;
        this.secret = secret;
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
