package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class GatewayAccessPaymentChannelQueryAdapterTest {

    @Test
    void shouldOnlySupportConfiguredChannelsWhenEnabled() {
        GatewayAccessPaymentChannelQueryAdapter adapter = new GatewayAccessPaymentChannelQueryAdapter(
                true, "http://gateway/query", "wx_h5, alipay_h5", "APP_PAY_CORE", "secret",
                Mockito.mock(RestTemplate.class));

        assertTrue(adapter.supports("WX_H5"));
        assertFalse(adapter.supports("offline_bank"));
        assertFalse(new GatewayAccessPaymentChannelQueryAdapter(
                false, "http://gateway/query", "wx_h5", Mockito.mock(RestTemplate.class))
                .supports("wx_h5"));
        assertFalse(new GatewayAccessPaymentChannelQueryAdapter(
                true, "http://gateway/query", "wx_h5", "APP_PAY_CORE", "", Mockito.mock(RestTemplate.class))
                .supports("wx_h5"));
    }

    @Test
    void shouldReturnGatewayTradeFact() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        PaymentChannelQueryResultDTO data = new PaymentChannelQueryResultDTO();
        data.setTradeStatus("SUCCESS");
        data.setChannelTransactionNo("WX-1001");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PaymentChannelQueryResultDTO result = new GatewayAccessPaymentChannelQueryAdapter(
                true, "http://gateway/query", "wx_h5", restTemplate)
                .query(paymentDetail());

        assertEquals("SUCCESS", result.getTradeStatus());
        assertEquals("WX-1001", result.getChannelTransactionNo());
        assertEquals("GATEWAY_ACCESS_HTTP", result.getQuerySource());
    }

    @Test
    void shouldRejectInvalidRequestBeforeGatewayCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter adapter = adapter(restTemplate);

        assertInvalidRequest(assertThrows(BusinessException.class, () -> adapter.query(null)));

        PaymentDetailDTO missingRequired = paymentDetail();
        missingRequired.setOrderNo("   ");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(missingRequired)));

        PaymentDetailDTO invalidOptional = paymentDetail();
        invalidOptional.setChannelTransactionNo("WX-1001\n");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(invalidOptional)));

        PaymentDetailDTO oversized = paymentDetail();
        oversized.setChannel(repeat("C", 65));
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.query(oversized)));

        verify(restTemplate, never()).exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class));
    }

    @Test
    void shouldSendNormalizedQueryRequest() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        AtomicReference<GatewayAccessPaymentChannelQueryAdapter.GatewayQueryRequest> sentRequest =
                new AtomicReference<>();
        stubSuccess(restTemplate, sentRequest);
        PaymentDetailDTO detail = paymentDetail();
        detail.setPaymentOrderId(" PAY-1001 ");
        detail.setOrderNo(" ORD-1001 ");
        detail.setChannel(" wx_h5 ");
        detail.setChannelTransactionNo(" WX-1001 ");

        adapter(restTemplate).query(detail);

        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryRequest sent = sentRequest.get();
        assertNotNull(sent);
        assertEquals("PAY-1001", sent.getPaymentOrderId());
        assertEquals("ORD-1001", sent.getOrderNo());
        assertEquals("wx_h5", sent.getChannelCode());
        assertEquals("WX-1001", sent.getChannelTransactionNo());
        assertEquals(" PAY-1001 ", detail.getPaymentOrderId());
        assertEquals(" wx_h5 ", detail.getChannel());
    }

    @Test
    void shouldRejectGatewayResponseWithoutTradeStatus() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        response.setCode("0");
        response.setData(new PaymentChannelQueryResultDTO());
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new GatewayAccessPaymentChannelQueryAdapter(
                        true, "http://gateway/query", "wx_h5", restTemplate)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        assertEquals(0, exception.getSuppressed().length);
    }

    @Test
    void shouldRejectRawGatewayTradeStatusWithTrailingControlCharacter() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        PaymentChannelQueryResultDTO data = new PaymentChannelQueryResultDTO();
        data.setTradeStatus("SUCCESS\n");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new GatewayAccessPaymentChannelQueryAdapter(
                        true, "http://gateway/query", "wx_h5", restTemplate)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectRawGatewayQuerySourceWithTrailingControlCharacter() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        PaymentChannelQueryResultDTO data = new PaymentChannelQueryResultDTO();
        data.setTradeStatus("SUCCESS");
        data.setQuerySource("GATEWAY_ACCESS_HTTP\n");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new GatewayAccessPaymentChannelQueryAdapter(
                        true, "http://gateway/query", "wx_h5", restTemplate)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectGatewayBusinessFailure() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        response.setCode("GATEWAY_ACCESS_DENIED");
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new GatewayAccessPaymentChannelQueryAdapter(
                        true, "http://gateway/query", "wx_h5", "APP_PAY_CORE", "secret", restTemplate)
                        .query(paymentDetail()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
    }

    private PaymentDetailDTO paymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-1001");
        detail.setOrderNo("ORD-1001");
        detail.setChannel("wx_h5");
        return detail;
    }

    private GatewayAccessPaymentChannelQueryAdapter adapter(RestTemplate restTemplate) {
        return new GatewayAccessPaymentChannelQueryAdapter(
                true, "http://gateway/query", "wx_h5", "APP_PAY_CORE", "secret", restTemplate);
    }

    private void stubSuccess(
            RestTemplate restTemplate,
            AtomicReference<GatewayAccessPaymentChannelQueryAdapter.GatewayQueryRequest> sentRequest) {
        GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse response =
                new GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse();
        PaymentChannelQueryResultDTO data = new PaymentChannelQueryResultDTO();
        data.setTradeStatus("SUCCESS");
        data.setChannelTransactionNo("WX-1001");
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/query"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelQueryAdapter.GatewayQueryResponse.class)))
                .thenAnswer(invocation -> {
                    HttpEntity<GatewayAccessPaymentChannelQueryAdapter.GatewayQueryRequest> entity =
                            invocation.getArgument(2);
                    sentRequest.set(entity.getBody());
                    return ResponseEntity.ok(response);
                });
    }

    private void assertInvalidRequest(BusinessException exception) {
        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("gateway-access payment query request is invalid", exception.getMessage());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
