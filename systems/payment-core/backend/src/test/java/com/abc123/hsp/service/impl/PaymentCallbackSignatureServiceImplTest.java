package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import com.abc123.hsp.mapper.PaymentCallbackSecurityMapper;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * 回调签名和防重放测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackSignatureServiceImplTest {

    @Mock
    private PaymentCallbackSecurityMapper paymentCallbackSecurityMapper;

    @Test
    void shouldVerifyValidSignedCallback() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-001",
                String.valueOf(Instant.now().getEpochSecond()));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                "",
                300,
                600
        );

        assertDoesNotThrow(() -> service.verify("wx_h5", request));
        verify(paymentCallbackSecurityMapper).deleteExpiredNonce();
        verify(paymentCallbackSecurityMapper).insertCallbackNonce("wx_h5", "nonce-001", "PAY-TEST-001", 600);
    }

    @Test
    void shouldRejectReplayNonce() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-002", timestamp);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate nonce"))
                .when(paymentCallbackSecurityMapper)
                .insertCallbackNonce("wx_h5", "nonce-002", "PAY-TEST-001", 600);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                "",
                300,
                600
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_NONCE_REPLAY, exception.getCode());
    }

    @Test
    void shouldRejectExpiredTimestamp() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest(
                "wx_h5",
                "test-secret",
                "nonce-003",
                String.valueOf(Instant.now().minusSeconds(1000).getEpochSecond()));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                "",
                300,
                600
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, exception.getCode());
    }

    @Test
    void shouldFallbackToGlobalSecretWhenChannelSecretMissing() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "fallback-secret", "nonce-004",
                String.valueOf(Instant.now().getEpochSecond()));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", ""));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                "fallback-secret",
                300,
                600
        );

        assertDoesNotThrow(() -> service.verify("WX_H5", request));
    }

    @Test
    void shouldVerifyRsa2SignedCallback() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        PaymentCallbackRequestDTO request = buildRsaRequest("alipay_h5", keyPair, "nonce-005", timestamp);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("alipay_h5"))
                .thenReturn(buildRsaProfile("alipay_h5", publicKey));

        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                "",
                300,
                600
        );

        assertDoesNotThrow(() -> service.verify("alipay_h5", request));
    }

    private PaymentCallbackSecurityProfileDTO buildHmacProfile(String channelCode, String secret) {
        PaymentCallbackSecurityProfileDTO profile = new PaymentCallbackSecurityProfileDTO();
        profile.setChannelCode(channelCode);
        profile.setCallbackSignAlgorithm("HMAC-SHA256");
        profile.setCallbackSecret(secret);
        profile.setNotifySignWindowSec(300);
        return profile;
    }

    private PaymentCallbackSecurityProfileDTO buildRsaProfile(String channelCode, String publicKey) {
        PaymentCallbackSecurityProfileDTO profile = new PaymentCallbackSecurityProfileDTO();
        profile.setChannelCode(channelCode);
        profile.setCallbackSignAlgorithm("RSA2");
        profile.setCallbackPublicKey(publicKey);
        profile.setNotifySignWindowSec(300);
        return profile;
    }

    private PaymentCallbackRequestDTO buildRsaRequest(
            String channel,
            KeyPair keyPair,
            String nonce,
            String timestamp) throws Exception {
        PaymentCallbackRequestDTO request = new PaymentCallbackRequestDTO();
        request.setPaymentOrderId("PAY-TEST-001");
        request.setTradeStatus("SUCCESS");
        request.setChannelTransactionNo("CHANNEL-TEST-001");
        request.setNonce(nonce);
        request.setTimestamp(timestamp);
        String payload = String.join("|",
                channel,
                request.getPaymentOrderId(),
                request.getTradeStatus(),
                request.getChannelTransactionNo(),
                request.getTimestamp(),
                request.getNonce());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(payload.getBytes(StandardCharsets.UTF_8));
        request.setSignature(Base64.getEncoder().encodeToString(signature.sign()));
        return request;
    }

    private PaymentCallbackRequestDTO buildRequest(
            String channel,
            String secret,
            String nonce,
            String timestamp) throws Exception {
        PaymentCallbackRequestDTO request = new PaymentCallbackRequestDTO();
        request.setPaymentOrderId("PAY-TEST-001");
        request.setTradeStatus("SUCCESS");
        request.setChannelTransactionNo("CHANNEL-TEST-001");
        request.setNonce(nonce);
        request.setTimestamp(timestamp);
        request.setSignature(sign(String.join("|",
                channel,
                request.getPaymentOrderId(),
                request.getTradeStatus(),
                request.getChannelTransactionNo(),
                request.getTimestamp(),
                request.getNonce()), secret));
        return request;
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
