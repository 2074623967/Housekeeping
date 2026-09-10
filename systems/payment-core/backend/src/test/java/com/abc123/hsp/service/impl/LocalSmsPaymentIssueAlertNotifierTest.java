package com.abc123.hsp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 短信异常告警通知器测试。
 */
class LocalSmsPaymentIssueAlertNotifierTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldFailClosedWhenWebhookNotConfigured() {
        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(Mockito.mock(RestTemplate.class), "")
                .send(buildDispatchItem());

        Assertions.assertEquals("FAILED", result.getProviderDeliveryStatus());
        Assertions.assertNull(result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("Webhook 未配置"));
        Assertions.assertEquals("CONFIGURATION_ERROR:WEBHOOK_URL_MISSING", result.getProviderReceiptSnapshot());
    }

    @Test
    void shouldAllowLocalReceiptWhenDemoBehaviorExplicitlyEnabled() {
        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                Mockito.mock(RestTemplate.class), "", true).send(buildDispatchItem());

        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("本地SMS通知器已受理"));
        Assertions.assertEquals("LOCAL:SMS:ACCEPTED", result.getProviderReceiptSnapshot());
    }

    @Test
    void shouldPostToHttpGatewayWhenConfigured() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://sms.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"SUCCESS\",\"data\":{\"smsId\":\"SMS-EXT-001\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                restTemplate,
                "https://sms.example.com/payment-alert",
                5200,
                "/code",
                "SUCCESS",
                "/data/smsId",
                "X-Access-Key",
                "sms-key-001",
                "X-Sms-Signature",
                "sms-secret",
                "HMAC_SHA256",
                "X-Sms-Timestamp",
                "X-Sms-Nonce",
                "/data/deliveryStatus",
                "SENT,DELIVERED",
                "QUEUED,ACCEPTED",
                "FAILED,REJECTED",
                "/data/errorCode"
        ).send(buildDispatchItem());

        ArgumentCaptor<HttpEntity> payloadCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                Mockito.eq("https://sms.example.com/payment-alert"),
                payloadCaptor.capture(),
                Mockito.eq(String.class)
        );
        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertEquals("SMS-EXT-001", result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderReceiptSnapshot().contains("HTTP_RESPONSE:{\"code\":\"SUCCESS\""));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("HTTP=200"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("timeout=5200ms"));
        Assertions.assertTrue(payloadCaptor.getValue().toString().contains("PAY-001"));
        Assertions.assertEquals("sms-key-001", payloadCaptor.getValue().getHeaders().getFirst("X-Access-Key"));
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Sms-Signature"));
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Sms-Timestamp"));
        Assertions.assertTrue(payloadCaptor.getValue().getHeaders().containsKey("X-Sms-Nonce"));
        Assertions.assertEquals(
                signWithTimestampAndNonce(
                        payloadCaptor.getValue().getBody(),
                        payloadCaptor.getValue().getHeaders().getFirst("X-Sms-Timestamp"),
                        payloadCaptor.getValue().getHeaders().getFirst("X-Sms-Nonce"),
                        "sms-secret"),
                payloadCaptor.getValue().getHeaders().getFirst("X-Sms-Signature"));
    }

    @Test
    void shouldReturnFailedStatusWhenSmsWebhookBusinessCodeMismatch() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://sms.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"ERROR\",\"data\":{\"smsId\":\"SMS-EXT-ERR\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                restTemplate,
                "https://sms.example.com/payment-alert",
                5200,
                "/code",
                "SUCCESS",
                "/data/smsId",
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
        ).send(buildDispatchItem());

        Assertions.assertEquals("FAILED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("businessCheck=期望=SUCCESS，实际=ERROR"));
    }

    @Test
    void shouldReturnFailedStatusWhenSmsWebhookHttpStatusIsNot2xx() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://sms.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"code\":\"SUCCESS\",\"data\":{\"smsId\":\"SMS-EXT-500\",\"deliveryStatus\":\"QUEUED\",\"errorCode\":\"SMS_500\"}}", HttpStatus.BAD_GATEWAY));

        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                restTemplate,
                "https://sms.example.com/payment-alert",
                5200,
                "/code",
                "SUCCESS",
                "/data/smsId",
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
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("HTTP=502"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("failureCode=SMS_500"));
    }

    @Test
    void shouldFailWhenProviderReturnsUnknownDeliveryStatus() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(Mockito.anyString(), Mockito.any(), Mockito.eq(String.class)))
                .thenReturn(new ResponseEntity<String>(
                        "{\"code\":\"SUCCESS\",\"data\":{\"deliveryStatus\":\"MYSTERY\"}}",
                        HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                restTemplate,
                "https://sms.example.com/payment-alert",
                3000,
                "/code",
                "SUCCESS",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "/data/deliveryStatus",
                "SENT",
                "QUEUED",
                "FAILED",
                ""
        ).send(buildDispatchItem());

        Assertions.assertEquals("FAILED", result.getProviderDeliveryStatus());
        Assertions.assertNull(result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("providerStatus=MYSTERY"));
    }

    @Test
    void shouldReturnFailedStatusWhenSmsWebhookTransportFails() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://sms.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenThrow(new ResourceAccessException("Connection reset"));

        PaymentIssueAlertDeliveryResultDTO result = new LocalSmsPaymentIssueAlertNotifier(
                restTemplate,
                "https://sms.example.com/payment-alert",
                5200,
                "/code",
                "SUCCESS",
                "/data/smsId",
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
        ).send(buildDispatchItem());

        Assertions.assertEquals("FAILED", result.getProviderDeliveryStatus());
        Assertions.assertNull(result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("transportError=[REDACTED]"));
        Assertions.assertFalse(result.getProviderDeliveryMessage().contains("Connection reset"));
        Assertions.assertEquals("HTTP_TRANSPORT_ERROR:[REDACTED]", result.getProviderReceiptSnapshot());
        Assertions.assertFalse(result.getProviderReceiptSnapshot().contains("Connection reset"));
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
        item.setProviderCode("ALERT_SMS_TENCENT");
        item.setTemplateCode("TPL_PAYMENT_ISSUE_SMS_V1");
        item.setAlertContent("支付异常 ISSUE-001 已超过 P1 SLA，请进入异常中心处理。");
        item.setRenderedAlertContent("【P1】支付单 PAY-001 异常，请及时处理。");
        return item;
    }

    private String signWithTimestampAndNonce(Object body, String timestamp, String nonce, String secret) {
        try {
            String payloadJson = OBJECT_MAPPER.writeValueAsString(body);
            String canonicalPayload = timestamp + "\n" + nonce + "\n" + payloadJson;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
