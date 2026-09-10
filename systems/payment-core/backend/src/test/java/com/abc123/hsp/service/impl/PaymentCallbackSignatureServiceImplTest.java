package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackSecurityProfileDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
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
                601
        );

        assertDoesNotThrow(() -> service.verify("wx_h5", request));
        verify(paymentCallbackSecurityMapper).deleteExpiredNonce("wx_h5", "nonce-001");
        verify(paymentCallbackSecurityMapper).insertCallbackNonce(
                "wx_h5", "nonce-001", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldVerifyValidSignedRefundCallbackAndRecordRefundBusinessType() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-TEST-001");
        request.setRefundStatus("SUCCESS");
        request.setChannelRefundNo("CHANNEL-REFUND-001");
        request.setNonce("nonce-refund-001");
        request.setTimestamp(timestamp);
        request.setSignature(sign(String.join("|",
                "wx_h5",
                request.getRefundOrderId(),
                request.getRefundStatus(),
                request.getChannelRefundNo(),
                timestamp,
                request.getNonce()), "test-secret"));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5"))
                .thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper, true, 601);

        assertDoesNotThrow(() -> service.verifyRefund("WX_H5", request));

        verify(paymentCallbackSecurityMapper).insertCallbackNonce(
                "wx_h5", "nonce-refund-001", "REFUND", "REF-TEST-001", 601);
    }

    @Test
    void shouldRejectReplayNonce() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-002", timestamp);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate nonce"))
                .when(paymentCallbackSecurityMapper)
                .insertCallbackNonce("wx_h5", "nonce-002", "PAYMENT", "PAY-TEST-001", 601);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
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
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, exception.getCode());
    }

    @Test
    void shouldRejectTimestampWhenDifferenceOverflowsLong() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest(
                "wx_h5",
                "test-secret",
                "nonce-overflow",
                String.valueOf(Long.MIN_VALUE));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5"))
                .thenReturn(buildHmacProfile("wx_h5", "test-secret"));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-overflow", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectCallbackWhenChannelSecretMissing() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "fallback-secret", "nonce-004",
                String.valueOf(Instant.now().getEpochSecond()));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(buildHmacProfile("wx_h5", ""));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("WX_H5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SECRET_MISSING, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-004", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectCallbackWhenSignatureAlgorithmMissing() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-005",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSecurityProfileDTO profile = buildHmacProfile("wx_h5", "test-secret");
        profile.setCallbackSignAlgorithm(" ");
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(profile);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_ALGORITHM_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-005", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectCallbackWhenSignatureAlgorithmUnsupported() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-006",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSecurityProfileDTO profile = buildHmacProfile("wx_h5", "test-secret");
        profile.setCallbackSignAlgorithm("RSA-SHA1");
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(profile);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_ALGORITHM_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-006", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldVerifyRsa2SignedCallback() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        PaymentCallbackRequestDTO request = buildRsaRequest("alipay_h5", keyPair, "nonce-007", timestamp);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("alipay_h5"))
                .thenReturn(buildRsaProfile("alipay_h5", publicKey));

        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        assertDoesNotThrow(() -> service.verify("alipay_h5", request));
    }

    @Test
    void shouldRejectMalformedRsaSignatureAsBusinessError() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        PaymentCallbackRequestDTO request = buildRsaRequest(
                "alipay_h5",
                keyPair,
                "nonce-rsa-signature",
                String.valueOf(Instant.now().getEpochSecond()));
        request.setSignature("not-base64!");
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("alipay_h5"))
                .thenReturn(buildRsaProfile("alipay_h5", publicKey));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify("alipay_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("alipay_h5", "nonce-rsa-signature", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectWrongLengthRsaSignatureAsBusinessError() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        PaymentCallbackRequestDTO request = buildRsaRequest(
                "alipay_h5",
                keyPair,
                "nonce-rsa-length",
                String.valueOf(Instant.now().getEpochSecond()));
        request.setSignature(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("alipay_h5"))
                .thenReturn(buildRsaProfile("alipay_h5", publicKey));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify("alipay_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("alipay_h5", "nonce-rsa-length", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectMalformedRsaPublicKeyAsBusinessError() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest(
                "alipay_h5",
                "unused-secret",
                "nonce-rsa-key",
                String.valueOf(Instant.now().getEpochSecond()));
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("alipay_h5"))
                .thenReturn(buildRsaProfile("alipay_h5", "not-a-public-key"));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.verify("alipay_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_CREDENTIAL_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("alipay_h5", "nonce-rsa-key", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectMissingChannelSignatureWindow() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-window-missing",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSecurityProfileDTO profile = buildHmacProfile("wx_h5", "test-secret");
        profile.setNotifySignWindowSec(null);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(profile);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-window-missing", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectNonPositiveChannelSignatureWindow() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-window-zero",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSecurityProfileDTO profile = buildHmacProfile("wx_h5", "test-secret");
        profile.setNotifySignWindowSec(0);
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(profile);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-window-zero", "PAYMENT", "PAY-TEST-001", 601);
    }

    @Test
    void shouldRejectNonceTtlThatDoesNotOutlastFullSignatureValidityPeriod() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-ttl-short",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSecurityProfileDTO profile = buildHmacProfile("wx_h5", "test-secret");
        when(paymentCallbackSecurityMapper.findCallbackSecurityProfileByChannelCode("wx_h5")).thenReturn(profile);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                600
        );

        BusinessException exception = assertThrows(BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID, exception.getCode());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
        verify(paymentCallbackSecurityMapper, never())
                .insertCallbackNonce("wx_h5", "nonce-ttl-short", "PAYMENT", "PAY-TEST-001", 600);
    }

    @Test
    void shouldRejectNonPositiveNonceTtlAtStartup() {
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackSignatureServiceImpl(paymentCallbackSecurityMapper, true, 0));
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackSignatureServiceImpl(paymentCallbackSecurityMapper, true, -1));

        assertEquals("payment callback nonce TTL must be positive", zeroException.getMessage());
        assertEquals("payment callback nonce TTL must be positive", negativeException.getMessage());
    }

    @Test
    void shouldDelegateBoundedExpiredNonceCleanup() {
        when(paymentCallbackSecurityMapper.deleteExpiredNonceBatch(1000)).thenReturn(12);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );

        assertEquals(12, service.cleanupExpiredNonces(1000));
        verify(paymentCallbackSecurityMapper).deleteExpiredNonceBatch(1000);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> service.cleanupExpiredNonces(0));
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class, () -> service.cleanupExpiredNonces(-1));
        assertEquals("payment callback nonce cleanup batch size must be positive", exception.getMessage());
        assertEquals("payment callback nonce cleanup batch size must be positive", negativeException.getMessage());
    }

    @Test
    void shouldRejectOversizedSecurityFieldsBeforeConfigurationLookup() throws Exception {
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                paymentCallbackSecurityMapper,
                true,
                601
        );
        PaymentCallbackRequestDTO request = buildRequest(
                "wx_h5",
                "test-secret",
                repeat('N', 129),
                String.valueOf(Instant.now().getEpochSecond()));

        BusinessException nullRequestException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", null));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, nullRequestException.getCode());
        BusinessException nonceException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, nonceException.getCode());
        request.setNonce("nonce\ninvalid");
        BusinessException nonceControlException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, nonceControlException.getCode());
        request.setNonce("nonce-valid");
        request.setSignature(repeat('S', 8193));
        BusinessException signatureException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID, signatureException.getCode());
        request.setSignature("signature-valid");
        request.setTimestamp(repeat('1', 21));
        BusinessException timestampException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, timestampException.getCode());
        request.setTimestamp("1234567890\n");
        BusinessException timestampControlException = assertThrows(
                BusinessException.class, () -> service.verify("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_TIMESTAMP_INVALID, timestampControlException.getCode());
        request.setTimestamp(String.valueOf(Instant.now().getEpochSecond()));
        BusinessException channelException = assertThrows(
                BusinessException.class, () -> service.verify(repeat('C', 65), request));
        assertEquals(ErrorCode.PAYMENT_CALLBACK_CHANNEL_MISSING, channelException.getCode());

        verify(paymentCallbackSecurityMapper, never())
                .findCallbackSecurityProfileByChannelCode(org.mockito.ArgumentMatchers.anyString());
        verify(paymentCallbackSecurityMapper, never()).deleteExpiredNonce(anyString(), anyString());
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

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
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

    private String repeat(char value, int count) {
        char[] characters = new char[count];
        java.util.Arrays.fill(characters, value);
        return new String(characters);
    }
}
