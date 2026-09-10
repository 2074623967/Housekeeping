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
import org.mockito.Mockito;
import org.springframework.web.client.RestTemplate;

class GatewayAccessRefundChannelQueryAdapterTest {

    @Test
    void shouldOnlySupportConfiguredChannelsWhenEnabled() {
        GatewayAccessRefundChannelQueryAdapter adapter = new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5, alipay_h5", Mockito.mock(RestTemplate.class));

        assertTrue(adapter.supports("WX_H5"));
        assertFalse(adapter.supports("offline_bank"));
        assertFalse(new GatewayAccessRefundChannelQueryAdapter(
                false, "http://gateway/refunds/query", "wx_h5", Mockito.mock(RestTemplate.class))
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
        response.setData(data);
        when(restTemplate.postForObject(
                eq("http://gateway/refunds/query"),
                any(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(response);

        RefundChannelQueryResultDTO result = new GatewayAccessRefundChannelQueryAdapter(
                true, "http://gateway/refunds/query", "wx_h5", restTemplate)
                .query(refund());

        assertEquals("SUCCESS", result.getRefundStatus());
        assertEquals("CHANNEL-REF-001", result.getChannelRefundNo());
        assertEquals("GATEWAY_ACCESS_HTTP", result.getQuerySource());
    }

    @Test
    void shouldRejectEmptyOrFailedGatewayResponse() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        when(restTemplate.postForObject(
                eq("http://gateway/refunds/query"),
                any(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenReturn(new GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse());

        BusinessException empty = assertThrows(BusinessException.class,
                () -> new GatewayAccessRefundChannelQueryAdapter(
                        true, "http://gateway/refunds/query", "wx_h5", restTemplate).query(refund()));
        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, empty.getCode());

        when(restTemplate.postForObject(
                eq("http://gateway/refunds/query"),
                any(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryRequest.class),
                eq(GatewayAccessRefundChannelQueryAdapter.GatewayRefundQueryResponse.class)))
                .thenThrow(new IllegalStateException("remote failure"));
        BusinessException failed = assertThrows(BusinessException.class,
                () -> new GatewayAccessRefundChannelQueryAdapter(
                        true, "http://gateway/refunds/query", "wx_h5", restTemplate).query(refund()));
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
