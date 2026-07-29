package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import com.abc123.hsp.mapper.PaymentCallbackSecurityMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付回调签名校验实现，支持 HMAC-SHA256 与 RSA2。
 */
@Service
public class PaymentCallbackSignatureServiceImpl implements PaymentCallbackSignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String RSA2 = "RSA2";
    private static final String RSA_SHA256 = "SHA256withRSA";

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
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, "callback signature fields are required");
        }
        String normalizedChannel = normalizeChannel(channel);
        PaymentCallbackSecurityProfileDTO securityProfile = resolveSecurityProfile(normalizedChannel);
        long timestampSeconds = parseTimestamp(request.getTimestamp());
        long nowSeconds = Instant.now().getEpochSecond();
        long allowedWindowSeconds = securityProfile.getNotifySignWindowSec() == null || securityProfile.getNotifySignWindowSec() <= 0
                ? allowedSkewSeconds
                : securityProfile.getNotifySignWindowSec().longValue();
        if (Math.abs(nowSeconds - timestampSeconds) > allowedWindowSeconds) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, "callback timestamp is out of allowed window");
        }
        String payload = String.join("|",
                normalizedChannel,
                request.getPaymentOrderId(),
                request.getTradeStatus(),
                request.getChannelTransactionNo(),
                request.getTimestamp(),
                request.getNonce());
        if (!verifySignature(payload, request.getSignature(), securityProfile)) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, "callback signature verification failed");
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
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_NONCE_REPLAY, "callback nonce replay detected");
        }
    }

    /**
     * 按秒级时间戳解析渠道回调时间。
     */
    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, "callback timestamp is invalid", exception);
        }
    }

    private PaymentCallbackSecurityProfileDTO resolveSecurityProfile(String normalizedChannel) {
        PaymentCallbackSecurityProfileDTO securityProfile = paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode(normalizedChannel);
        if (securityProfile == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECRET_MISSING, "callback security profile is not configured");
        }
        if (!StringUtils.hasText(securityProfile.getCallbackSignAlgorithm())) {
            securityProfile.setCallbackSignAlgorithm("HMAC-SHA256");
        }
        if (!StringUtils.hasText(securityProfile.getCallbackSecret()) && StringUtils.hasText(fallbackSecret)) {
            securityProfile.setCallbackSecret(fallbackSecret);
        }
        return securityProfile;
    }

    private String normalizeChannel(String channel) {
        if (!StringUtils.hasText(channel)) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_CHANNEL_MISSING, "callback channel is required");
        }
        return channel.trim().toLowerCase();
    }

    private boolean verifySignature(String payload, String actualSignature, PaymentCallbackSecurityProfileDTO securityProfile) {
        try {
            String algorithm = securityProfile.getCallbackSignAlgorithm().trim().toUpperCase();
            if (algorithm.contains(RSA2) || algorithm.contains("RSA")) {
                return verifyRsa(payload, actualSignature, securityProfile.getCallbackPublicKey());
            }
            return verifyHmac(payload, actualSignature, securityProfile.getCallbackSecret());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("unable to verify callback signature", exception);
        }
    }

    private boolean verifyHmac(String payload, String actualSignature, String signingSecret) throws Exception {
        if (!StringUtils.hasText(signingSecret)) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECRET_MISSING, "callback secret is not configured");
        }
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        String expectedSignature = Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), actualSignature.getBytes(StandardCharsets.UTF_8));
    }

    private boolean verifyRsa(String payload, String actualSignature, String publicKeyText) throws Exception {
        if (!StringUtils.hasText(publicKeyText)) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECRET_MISSING, "callback public key is not configured");
        }
        Signature signature = Signature.getInstance(RSA_SHA256);
        signature.initVerify(parsePublicKey(publicKeyText));
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        byte[] decodedSignature = Base64.getDecoder().decode(actualSignature);
        return signature.verify(decodedSignature);
    }

    private PublicKey parsePublicKey(String publicKeyText) throws Exception {
        String normalizedKey = publicKeyText.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decodedKey = Base64.getDecoder().decode(normalizedKey);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodedKey));
    }
}
