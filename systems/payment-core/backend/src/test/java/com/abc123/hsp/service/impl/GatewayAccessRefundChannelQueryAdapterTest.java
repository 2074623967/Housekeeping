package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class GatewayAccessRefundChannelQueryAdapterTest {

    @Test
    void shouldOnlySupportConfiguredChannelsWhenEnabled() {
        GatewayAccessRefundChannelQueryAdapter adapter = new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5, alipay_h5",
                "APP_PAY_CORE", "secret", Mockito.mock(RestTemplate.class));

        assertTrue(adapter.supports("WX_H5"));
        assertFalse(adapter.supports("offline_bank"));
        assertFalse(new GatewayAccessRefundChannelQueryAdapter(
                false, "http://gateway/refunds/query", "wx_h5",
                "APP_PAY_CORE", "secret", Mockito.mock(RestTemplate.class))
                .supports("wx_h5"));
        assertFalse(new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5",
                "APP_PAY_CORE", "", Mockito.mock(RestTemplate.class))
                .supports("wx_h5"));
    }

    @Test
    void shouldReturnNormalizedGatewayRefundFact() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse response =
                new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse();
        RefundChannelQueryResultDTO data = new RefundChannelQueryResultDTO();
        data.setRefundStatus(" success ");
        data.setChannelRefundNo(" CHANNEL-REF-001 ");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        RefundChannelQueryResultDTO result = new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5",
                "APP_PAY_CORE", "secret", restTemplate)
                .query(refund());

        assertEquals("SUCCESS", result.getRefundStatus());
        assertEquals("CHANNEL-REF-001", result.getChannelRefundNo());
        assertEquals("GATEWAY_ACCESS_HTTP", result.getQuerySource());
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        Mockito.verify(restTemplate).exchange(
                eq("http://gateway/refunds/query"), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class));
        assertEquals("APP_PAY_CORE", entityCaptor.getValue().getHeaders().getFirst("X-Gateway-App-Code"));
        assertEquals("secret", entityCaptor.getValue().getHeaders().getFirst("X-Gateway-Access-Key"));
    }

    @Test
    void shouldForwardUnknownAcceptanceQueryWithoutChannelRefundNumber() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse response =
                new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse();
        RefundChannelQueryResultDTO data = new RefundChannelQueryResultDTO();
        data.setRefundStatus("PROCESSING");
        data.setChannelRefundNo("CHANNEL-REF-RECOVERED");
        response.setCode("0");
        response.setData(data);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        RefundCallbackSourceDTO refund = refund();
        refund.setChannelRefundNo(null);
        refund.setSubmitEventStatus("UNKNOWN");

        RefundChannelQueryResultDTO result = new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5",
                "APP_PAY_CORE", "secret", restTemplate).query(refund);

        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest forwarded =
                (GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest)
                        entityCaptor.getValue().getBody();
        assertEquals("REF-001", forwarded.getRefundOrderId());
        assertEquals("RFA-001", forwarded.getRefundAttemptNo());
        assertEquals(null, forwarded.getChannelRefundNo());
        assertEquals("CHANNEL-REF-RECOVERED", result.getChannelRefundNo());
    }

    @Test
    void shouldRejectInvalidRequestBeforeGatewayCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelQueryAdapter adapter = adapter(restTemplate);

        assertInvalidRequest(assertThrows(BusinessException.class, () -> adapter.query(null)));

        RefundCallbackSourceDTO missingRequired = refund();
        missingRequired.setChannelCode("   ");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(missingRequired)));

        RefundCallbackSourceDTO invalidOptional = refund();
        invalidOptional.setChannelRefundNo("CHANNEL-REF-001\n");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(invalidOptional)));

        RefundCallbackSourceDTO oversized = refund();
        oversized.setRefundOrderId(repeat("R", 65));
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(oversized)));

        verify(restTemplate, never()).exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class));
    }

    @Test
    void shouldSendNormalizedQueryRequestWithoutMutatingRefund() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse response =
                new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse();
        RefundChannelQueryResultDTO data = new RefundChannelQueryResultDTO();
        data.setRefundStatus("PROCESSING");
        data.setChannelRefundNo("CHANNEL-REF-001");
        response.setCode("0");
        response.setData(data);
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));
        RefundCallbackSourceDTO refund = refund();
        refund.setRefundOrderId(" REF-001 ");
        refund.setRefundAttemptNo(" RFA-001 ");
        refund.setChannelCode(" wx_h5 ");
        refund.setChannelRefundNo(" CHANNEL-REF-001 ");

        adapter(restTemplate).query(refund);

        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest sent =
                (GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest)
                        entityCaptor.getValue().getBody();
        assertEquals("REF-001", sent.getRefundOrderId());
        assertEquals("RFA-001", sent.getRefundAttemptNo());
        assertEquals("wx_h5", sent.getChannelCode());
        assertEquals("CHANNEL-REF-001", sent.getChannelRefundNo());
        assertEquals(" REF-001 ", refund.getRefundOrderId());
        assertEquals(" RFA-001 ", refund.getRefundAttemptNo());
        assertEquals(" wx_h5 ", refund.getChannelCode());
    }

    @Test
    void shouldRejectEmptyOrFailedGatewayResponse() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(
                        new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse()));

        BusinessException empty = assertThrows(BusinessException.class,
                () -> new GatewayAccessRefundChannelQueryAdapter(
                        true, "http://gateway/refunds/query", "wx_h5",
                        "APP_PAY_CORE", "secret", restTemplate).query(refund()));
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, empty.getCode());
        assertNull(empty.getCause());
        assertEquals(0, empty.getStackTrace().length);
        assertEquals(0, empty.getSuppressed().length);

        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenThrow(new IllegalStateException("remote failure"));
        BusinessException failed = assertThrows(BusinessException.class,
                () -> new GatewayAccessRefundChannelQueryAdapter(
                        true, "http://gateway/refunds/query", "wx_h5",
                        "APP_PAY_CORE", "secret", restTemplate).query(refund()));
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, failed.getCode());
        assertNull(failed.getCause());
        assertEquals(0, failed.getStackTrace().length);
        assertEquals(0, failed.getSuppressed().length);
    }

    @Test
    void shouldRejectRawGatewayQuerySourceWithTrailingControlCharacter() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse response =
                new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse();
        RefundChannelQueryResultDTO data = new RefundChannelQueryResultDTO();
        data.setRefundStatus("PROCESSING");
        data.setChannelRefundNo("CHANNEL-REF-001");
        data.setQuerySource("GATEWAY_ACCESS_HTTP\n");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/refunds/query"),
                eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new GatewayAccessRefundChannelQueryAdapter(
                        true, "http://gateway/refunds/query", "wx_h5",
                        "APP_PAY_CORE", "secret", restTemplate).query(refund()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        assertEquals(0, exception.getSuppressed().length);
    }

    private RefundCallbackSourceDTO refund() {
        RefundCallbackSourceDTO refund = new RefundCallbackSourceDTO();
        refund.setRefundOrderId("REF-001");
        refund.setRefundAttemptNo("RFA-001");
        refund.setChannelCode("wx_h5");
        refund.setChannelRefundNo("CHANNEL-REF-001");
        refund.setStatus("PROCESSING");
        return refund;
    }

    private GatewayAccessRefundChannelQueryAdapter adapter(RestTemplate restTemplate) {
        return new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5",
                "APP_PAY_CORE", "secret", restTemplate);
    }

    private void assertInvalidRequest(BusinessException exception) {
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("gateway-access refund query request is invalid", exception.getMessage());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
