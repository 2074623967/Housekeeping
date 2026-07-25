package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 邮件异常告警通知器测试。
 */
class LocalEmailPaymentIssueAlertNotifierTest {

    @Test
    void shouldFallbackToLocalReceiptWhenWebhookNotConfigured() {
        PaymentIssueAlertDeliveryResultDTO result = new LocalEmailPaymentIssueAlertNotifier(Mockito.mock(RestTemplate.class), "")
                .send(buildDispatchItem());

        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("本地EMAIL通知器已受理"));
    }

    @Test
    void shouldPostToHttpGatewayWhenConfigured() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForEntity(
                Mockito.eq("https://email.example.com/payment-alert"),
                Mockito.any(),
                Mockito.eq(String.class)
        )).thenReturn(new ResponseEntity<String>("{\"status\":\"DELIVERED\",\"result\":{\"mailNo\":\"EMAIL-EXT-001\"}}", HttpStatus.OK));

        PaymentIssueAlertDeliveryResultDTO result = new LocalEmailPaymentIssueAlertNotifier(
                restTemplate,
                "https://email.example.com/payment-alert",
                6100,
                "/status",
                "DELIVERED",
                "/result/mailNo"
        ).send(buildDispatchItem());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                Mockito.eq("https://email.example.com/payment-alert"),
                payloadCaptor.capture(),
                Mockito.eq(String.class)
        );
        Assertions.assertEquals("ACCEPTED", result.getProviderDeliveryStatus());
        Assertions.assertEquals("EMAIL-EXT-001", result.getProviderReceiptNo());
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("HTTP=200"));
        Assertions.assertTrue(result.getProviderDeliveryMessage().contains("timeout=6100ms"));
        Assertions.assertTrue(payloadCaptor.getValue().toString().contains("PAY-001"));
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
        item.setProviderCode("ALERT_EMAIL_SENDCLOUD");
        item.setTemplateCode("TPL_PAYMENT_ISSUE_EMAIL_V1");
        item.setAlertContent("支付异常 ISSUE-001 已超过 P1 SLA，请进入异常中心处理。");
        item.setRenderedAlertContent("主题：P1 待回调未收口\n支付单号：PAY-001\n请及时处理。");
        return item;
    }
}
