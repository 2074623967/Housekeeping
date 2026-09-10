package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    }

    private RefundCallbackSourceDTO refund() {
        RefundCallbackSourceDTO refund = new RefundCallbackSourceDTO();
        refund.setRefundOrderId("REF-001");
        refund.setChannelCode("wx_h5");
        refund.setChannelRefundNo("CHANNEL-REF-001");
        refund.setStatus("PROCESSING");
        return refund;
    }
}
