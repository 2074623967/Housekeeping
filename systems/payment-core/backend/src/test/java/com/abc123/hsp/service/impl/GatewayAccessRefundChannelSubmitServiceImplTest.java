package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class GatewayAccessRefundChannelSubmitServiceImplTest {

    @Test
    void shouldFailClosedUnlessFullyConfigured() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);

        assertFailure(new GatewayAccessRefundChannelSubmitServiceImpl(
                false, "http://gateway/submit", "wx_h5", "APP_PAY_CORE", "secret", restTemplate));
        assertFailure(new GatewayAccessRefundChannelSubmitServiceImpl(
                true, "http://gateway/submit", "wx_h5", "APP_PAY_CORE", "", restTemplate));
        assertFailure(new GatewayAccessRefundChannelSubmitServiceImpl(
                true, "http://gateway/submit", "alipay_h5", "APP_PAY_CORE", "secret", restTemplate));
    }

    @Test
    void shouldReturnGatewayAcceptanceWithMinimalPayload() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RefundChannelSubmitResultDTO data = new RefundChannelSubmitResultDTO();
        data.setChannelRefundNo("WX-R-1001");
        data.setStatus("processing");
        data.setStatusType("WARN");
        GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse response =
                new GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        RefundChannelSubmitResultDTO result = adapter(restTemplate).submit(request());

        assertEquals("WX-R-1001", result.getChannelRefundNo());
        assertEquals("PROCESSING", result.getStatus());
        assertEquals("warn", result.getStatusType());
        assertTrue(result.getResponsePayload().contains("\"source\":\"gateway-access\""));
        assertFalse(result.getResponsePayload().contains("secret"));
    }

    @Test
    void shouldRejectGatewayBusinessFailure() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse response =
                new GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse();
        response.setCode("GATEWAY_ACCESS_DENIED");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        RefundChannelSubmitException exception = assertThrows(RefundChannelSubmitException.class,
                () -> adapter(restTemplate).submit(request()));

        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertFalse(exception.isResultUnknown());
    }

    @Test
    void shouldClassifyUpstreamFailureAsUnknown() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        String sensitiveDetail = "jdbc:mysql://internal-db/refund?password=submit-secret\n"
                + "POST /refunds token=submit-token response={sensitive-body}";
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class)))
                .thenThrow(new IllegalStateException(sensitiveDetail));

        RefundChannelSubmitException exception = assertThrows(RefundChannelSubmitException.class,
                () -> adapter(restTemplate).submit(request()));

        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("gateway-access refund submit failed; query before retrying", exception.getMessage());
        assertFalse(exception.getMessage().contains(sensitiveDetail));
        assertTrue(exception.isResultUnknown());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        assertEquals(0, exception.getSuppressed().length);
    }

    @Test
    void shouldRejectControlCharacterInGatewayAcceptanceAsUnknown() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        RefundChannelSubmitResultDTO data = new RefundChannelSubmitResultDTO();
        data.setChannelRefundNo("WX-R-1001\n");
        data.setStatus("PROCESSING");
        data.setStatusType("warn");
        GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse response =
                new GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        RefundChannelSubmitException exception = assertThrows(
                RefundChannelSubmitException.class, () -> adapter(restTemplate).submit(request()));

        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertTrue(exception.isResultUnknown());
    }

    private void assertFailure(GatewayAccessRefundChannelSubmitServiceImpl service) {
        RefundChannelSubmitException exception = assertThrows(
                RefundChannelSubmitException.class, () -> service.submit(request()));
        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertFalse(exception.isResultUnknown());
    }

    private GatewayAccessRefundChannelSubmitServiceImpl adapter(RestTemplate restTemplate) {
        return new GatewayAccessRefundChannelSubmitServiceImpl(
                true, "http://gateway/submit", "wx_h5", "APP_PAY_CORE", "secret", restTemplate);
    }

    private RefundChannelSubmitRequestDTO request() {
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-1001");
        request.setChannelCode("wx_h5");
        return request;
    }
}
