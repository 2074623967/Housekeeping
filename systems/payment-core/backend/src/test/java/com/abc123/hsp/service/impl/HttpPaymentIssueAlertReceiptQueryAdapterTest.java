package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class HttpPaymentIssueAlertReceiptQueryAdapterTest {

    @Test
    void shouldOnlySupportConfiguredChannelsWithCompleteCredentials() {
        HttpPaymentIssueAlertReceiptQueryAdapter adapter = adapter(
                true, "http://notification-gateway/receipts/query", "IM,SMS", "secret",
                Mockito.mock(RestTemplate.class));

        assertTrue(adapter.supports("im"));
        assertTrue(adapter.supports(" SMS "));
        assertFalse(adapter.supports("EMAIL"));
        assertFalse(adapter(false, "http://notification-gateway/receipts/query", "IM", "secret",
                Mockito.mock(RestTemplate.class)).supports("IM"));
        assertFalse(adapter(true, "", "IM", "secret", Mockito.mock(RestTemplate.class)).supports("IM"));
        assertFalse(adapter(true, "http://notification-gateway/receipts/query", "IM", "",
                Mockito.mock(RestTemplate.class)).supports("IM"));
    }

    @Test
    void shouldReturnNormalizedAndMaskedReceiptFact() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse response =
                new HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse();
        PaymentIssueAlertDeliveryResultDTO data = new PaymentIssueAlertDeliveryResultDTO();
        data.setProviderDeliveryStatus(" delivered ");
        data.setProviderReceiptNo(" PROVIDER-001 ");
        data.setProviderDeliveryMessage("供应商确认送达");
        data.setProviderReceiptSnapshot("HTTP_RESPONSE:{\"accessKey\":\"secret\",\"status\":\"DELIVERED\"}");
        data.setRenderedContentSnapshot("must-not-overwrite-content");
        response.setCode("0");
        response.setData(data);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("http://notification-gateway/receipts/query"), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PaymentIssueAlertDeliveryResultDTO result = adapter(
                true, "http://notification-gateway/receipts/query", "IM", "secret", restTemplate)
                .query(acceptedDelivery());

        assertEquals("DELIVERED", result.getProviderDeliveryStatus());
        assertEquals("PROVIDER-001", result.getProviderReceiptNo());
        assertEquals("HTTP_RESPONSE:{\"accessKey\":\"[REDACTED]\",\"status\":\"DELIVERED\"}",
                result.getProviderReceiptSnapshot());
        assertNull(result.getRenderedContentSnapshot());
        HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryRequest forwarded =
                (HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryRequest) entityCaptor.getValue().getBody();
        assertEquals("PIA-IM-001", forwarded.getAlertNo());
        assertEquals("IM", forwarded.getAlertChannel());
        assertEquals("PROVIDER-001", forwarded.getProviderReceiptNo());
        assertEquals("APP_PAY_CORE",
                entityCaptor.getValue().getHeaders().getFirst("X-Gateway-App-Code"));
        assertEquals("secret",
                entityCaptor.getValue().getHeaders().getFirst("X-Gateway-Access-Key"));
    }

    @Test
    void shouldRejectGatewayFailureUnsupportedStatusAndInvalidReceipt() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse response =
                new HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse();
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        assertThrows(IllegalStateException.class,
                () -> adapter(true, "http://notification-gateway/query", "IM", "secret", restTemplate)
                        .query(acceptedDelivery()));

        PaymentIssueAlertDeliveryResultDTO data = new PaymentIssueAlertDeliveryResultDTO();
        data.setProviderDeliveryStatus("UNKNOWN");
        response.setCode("0");
        response.setData(data);
        assertThrows(IllegalStateException.class,
                () -> adapter(true, "http://notification-gateway/query", "IM", "secret", restTemplate)
                        .query(acceptedDelivery()));

        data.setProviderDeliveryStatus("DELIVERED");
        data.setProviderReceiptNo("PROVIDER-OTHER\n");
        assertThrows(IllegalStateException.class,
                () -> adapter(true, "http://notification-gateway/query", "IM", "secret", restTemplate)
                        .query(acceptedDelivery()));
    }

    @Test
    void shouldRejectInvalidRequestBeforeCallingGateway() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentIssueAlertLogEntity accepted = acceptedDelivery();
        accepted.setAlertNo("PIA-IM-001\n");

        assertThrows(IllegalStateException.class,
                () -> adapter(true, "http://notification-gateway/query", "IM", "secret", restTemplate)
                        .query(accepted));
        Mockito.verifyNoInteractions(restTemplate);
    }

    @Test
    void shouldHideTransportFailureDetails() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(HttpPaymentIssueAlertReceiptQueryAdapter.ReceiptQueryResponse.class)))
                .thenThrow(new IllegalStateException("credential=secret-provider-error"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adapter(true, "http://notification-gateway/query", "IM", "secret", restTemplate)
                        .query(acceptedDelivery()));

        assertEquals("通知集成网关未返回可信的供应商送达回执", exception.getMessage());
    }

    private HttpPaymentIssueAlertReceiptQueryAdapter adapter(boolean enabled,
                                                             String queryUrl,
                                                             String channels,
                                                             String accessKey,
                                                             RestTemplate restTemplate) {
        return new HttpPaymentIssueAlertReceiptQueryAdapter(
                enabled, queryUrl, channels, "APP_PAY_CORE", accessKey, restTemplate);
    }

    private PaymentIssueAlertLogEntity acceptedDelivery() {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo("PIA-IM-001");
        entity.setIssueNo("ISSUE-001");
        entity.setAlertChannel("IM");
        entity.setProviderCode("IM-PROVIDER");
        entity.setProviderReceiptNo("PROVIDER-001");
        entity.setProviderDeliveryStatus("ACCEPTED");
        return entity;
    }
}
