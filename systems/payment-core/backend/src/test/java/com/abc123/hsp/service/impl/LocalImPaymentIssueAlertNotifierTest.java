package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * IM 异常告警通知器测试。
 */
class LocalImPaymentIssueAlertNotifierTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RSA_PRIVATE_KEY = generateRsaPrivateKeyPem();

    @Test
    void shouldFallbackToLocalReceiptWhenWebhookNotConfigured() {
        PaymentIssueAlertDeliveryResultDTO result = new LocalImPaymentIssueAlertNotifier(Mockito.mock(RestTemplate.class), "")
                .send(buildDispatchItem());

        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("本地IM通知器已受理"));
    }

    @Test
    void shouldPostToWebhookWhenConfigured() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"0\",\"data\":{\"receiptNo\":\"IM-EXT-001\",\"deliveryStatus\":\"SENT\",\"errorCode\":\"0\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalImPaymentIssueAlertNotifier(
                restTemplate,
                "https://hooks.example.com/payment-alert",
                4500,
                "/code",
                "0",
                "/data/receiptNo",
                "Authorization",
                "Bearer im-token",
                "X-Signature",
                "im-secret",
                "HMAC_SHA1",
                "X-Timestamp",
                "X-Nonce",
                "/data/deliveryStatus",
                "SENT,DELIVERED",
                "QUEUED,ACCEPTED",
                "FAILED,REJECTED",
                "/data/errorCode"
        ).send(buildDispatchItem());

        ArgumentCaptor<HttpEntity> payloadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                payloadCaptor.capture(),
                Mockito.eq(String.class)
        );
        Assertions.assertEquals("DELIVERED", result.getProviderDeliveryStatus());
        Assertions.assertEquals("IM-EXT-001", result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("HTTP=200"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("providerStatus=SENT"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("timeout=4500ms"));
        Assertions.assertTrue(payloadCaptor.getValue().toString().contains("PAY-001"));
        Assertions.assertEquals("Bearer im-token", payloadCaptor.getValue().getHeaders().getFirst("Authorization"));
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Signature"));
        Assertions.assertEquals(
                signPayload(payloadCaptor.getValue(), "im-secret", "HmacSHA1"),
                payloadCaptor.getValue().getHeaders().getFirst("X-Signature")
        );
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Timestamp"));
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Nonce"));
    }

    @Test
    void shouldRejectWebhookWhenBusinessCodeMismatch() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"500\"}", HttpStatus.OK));

        IllegalStateException exception = Assertions.assertThrows(
                IllegalStateException.class,
                () -> new LocalImPaymentIssueAlertNotifier(
                        restTemplate,
                        "https://hooks.example.com/payment-alert",
                        4500,
                        "/code",
                        "0",
                        "/data/receiptNo",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        ""
                ).send(buildDispatchItem())
        );

        Assertions.assertTrue(exception.getMessage().contains("业务响应未通过"));
    }

    @Test
    void shouldReturnFailedStatusWhenWebhookProviderRejectsRequest() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"0\",\"data\":{\"receiptNo\":\"IM-EXT-002\",\"deliveryStatus\":\"REJECTED\",\"errorCode\":\"IM_429\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalImPaymentIssueAlertNotifier(
                restTemplate,
                "https://hooks.example.com/payment-alert",
                4500,
                "/code",
                "0",
                "/data/receiptNo",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "/data/deliveryStatus",
                "SENT,DELIVERED",
                "QUEUED,ACCEPTED",
                "FAILED,REJECTED",
                "/data/errorCode"
        ).send(buildDispatchItem());

        Assertions.assertEquals("FAILED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("providerStatus=REJECTED"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("failureCode=IM_429"));
    }

    @Test
    void shouldSignWebhookPayloadWithRsaAlgorithmWhenConfigured() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"0\",\"data\":{\"receiptNo\":\"IM-EXT-003\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalImPaymentIssueAlertNotifier(
                restTemplate,
                "https://hooks.example.com/payment-alert",
                4500,
                "/code",
                "0",
                "/data/receiptNo",
                "",
                "",
                "X-Signature",
                RSA_PRIVATE_KEY,
                "RSA2",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        ).send(buildDispatchItem());

        ArgumentCaptor<HttpEntity> payloadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                Mockito.eq("https://hooks.example.com/payment-alert"),
                payloadCaptor.capture(),
                Mockito.eq(String.class)
        );
        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Signature"));
        Assertions.assertFalse(payloadCaptor.getValue().getHeaders().getFirst("X-Signature").isEmpty());
    }

    private PaymentIssueAlertDispatchItemDTO buildDispatchItem() {
        PaymentIssueAlertDispatchItemDTO item = new PaymentIssueAlertDispatchItemDTO();
        item.setAlertNo("PIA-OUTBOX-001");
        item.setIssueNo("ISSUE-001");
        item.setPaymentOrderId("PAY-001");
        item.setIssueType("待回调未收口");
        item.setSeverity("P1");
        item.setResponsibilityGroup("支付后端值班组");
        item.setReceiver("支付技术负责人");
        item.setProviderCode("ALERT_IM_WECOM_P1");
        item.setTemplateCode("TPL_PAYMENT_ISSUE_IM_P1_V1");
        item.setAlertContent("支付异常 ISSUE-001 已超过 P1 SLA，请进入异常中心处理。");
        item.setRenderedAlertContent("[P1] 待回调未收口 PAY-001");
        return item;
    }

    private String signPayload(HttpEntity entity, String secret, String algorithm) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return Base64.getEncoder().encodeToString(mac.doFinal(OBJECT_MAPPER.writeValueAsBytes(entity.getBody())));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String generateRsaPrivateKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            KeyPair keyPair = generator.generateKeyPair();
            String base64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
