package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * 回调签名和防重放测试。
 */
class PaymentCallbackSignatureServiceImplTest {

    @Test
    void shouldVerifyValidSignedCallback() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-001",
                String.valueOf(Instant.now().getEpochSecond()));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                true,
                "test-secret",
                300,
                600
        );

        assertDoesNotThrow(() -> service.verify("wx_h5", request));
    }

    @Test
    void shouldRejectReplayNonce() throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        PaymentCallbackRequestDTO request = buildRequest("wx_h5", "test-secret", "nonce-002", timestamp);
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                true,
                "test-secret",
                300,
                600
        );

        service.verify("wx_h5", request);

        assertThrows(IllegalArgumentException.class, () -> service.verify("wx_h5", request));
    }

    @Test
    void shouldRejectExpiredTimestamp() throws Exception {
        PaymentCallbackRequestDTO request = buildRequest(
                "wx_h5",
                "test-secret",
                "nonce-003",
                String.valueOf(Instant.now().minusSeconds(1000).getEpochSecond()));
        PaymentCallbackSignatureServiceImpl service = new PaymentCallbackSignatureServiceImpl(
                true,
                "test-secret",
                300,
                600
        );

        assertThrows(IllegalArgumentException.class, () -> service.verify("wx_h5", request));
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
