package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import com.abc123.hsp.mapper.PaymentCallbackSecurityMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
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
    private static final String HMAC_SHA256_CONFIG = "HMAC-SHA256";
    private static final String RSA2 = "RSA2";
    private static final String RSA_SHA256 = "SHA256withRSA";

    private final boolean required;
    private final long nonceTtlSeconds;
    private final PaymentCallbackSecurityMapper paymentCallbackSecurityMapper;

    public PaymentCallbackSignatureServiceImpl(
            PaymentCallbackSecurityMapper paymentCallbackSecurityMapper,
            @Value("${payment.callback.require-signature:true}") boolean required,
            @Value("${payment.callback.nonce-ttl-seconds:601}") long nonceTtlSeconds) {
        if (nonceTtlSeconds <= 0) {
            throw new IllegalArgumentException("payment callback nonce TTL must be positive");
        }
        this.paymentCallbackSecurityMapper = paymentCallbackSecurityMapper;
        this.required = required;
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
        long allowedWindowSeconds = resolveNotifySignWindowSeconds(securityProfile);
        verifyTimestampWindow(timestampSeconds, nowSeconds, allowedWindowSeconds);
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

    private void verifyTimestampWindow(long timestampSeconds, long nowSeconds, long allowedWindowSeconds) {
        long differenceSeconds;
        try {
            differenceSeconds = Math.subtractExact(nowSeconds, timestampSeconds);
        } catch (ArithmeticException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID,
                    "callback timestamp is out of allowed window", exception);
        }
        if (differenceSeconds > allowedWindowSeconds || differenceSeconds < -allowedWindowSeconds) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID,
                    "callback timestamp is out of allowed window");
        }
    }

    private long resolveNotifySignWindowSeconds(PaymentCallbackSecurityProfileDTO securityProfile) {
        Integer notifySignWindowSec = securityProfile.getNotifySignWindowSec();
        if (notifySignWindowSec == null || notifySignWindowSec <= 0) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID,
                    "callback signature window must be positive");
        }
        long allowedWindowSeconds = notifySignWindowSec.longValue();
        if (nonceTtlSeconds <= allowedWindowSeconds * 2L) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID,
                    "callback nonce TTL must outlast the full signature validity period");
        }
        return allowedWindowSeconds;
    }

    private PaymentCallbackSecurityProfileDTO resolveSecurityProfile(String normalizedChannel) {
        PaymentCallbackSecurityProfileDTO securityProfile = paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode(normalizedChannel);
        if (securityProfile == null) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SECRET_MISSING, "callback security profile is not configured");
        }
        if (!StringUtils.hasText(securityProfile.getCallbackSignAlgorithm())) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_ALGORITHM_INVALID,
                    "callback signature algorithm is not configured");
        }
        String algorithm = securityProfile.getCallbackSignAlgorithm().trim().toUpperCase(Locale.ROOT);
        if (!HMAC_SHA256_CONFIG.equals(algorithm) && !RSA2.equals(algorithm)) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_ALGORITHM_INVALID,
                    "callback signature algorithm is unsupported");
        }
        securityProfile.setCallbackSignAlgorithm(algorithm);
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
            String algorithm = securityProfile.getCallbackSignAlgorithm();
            if (RSA2.equals(algorithm)) {
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
        byte[] decodedSignature;
        try {
            decodedSignature = Base64.getDecoder().decode(actualSignature);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID,
                    "callback RSA signature encoding is invalid", exception);
        }
        try {
            return signature.verify(decodedSignature);
        } catch (SignatureException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID,
                    "callback RSA signature is invalid", exception);
        }
    }

    private PublicKey parsePublicKey(String publicKeyText) {
        String normalizedKey = publicKeyText.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] decodedKey = Base64.getDecoder().decode(normalizedKey);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decodedKey));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_CALLBACK_CREDENTIAL_INVALID,
                    "callback RSA public key is invalid", exception);
        }
    }
}
