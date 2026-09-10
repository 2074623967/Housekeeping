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
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class GatewayAccessPaymentChannelSubmitAdapterTest {

    @Test
    void shouldOnlySupportFullyConfiguredChannels() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelSubmitAdapter adapter = new GatewayAccessPaymentChannelSubmitAdapter(
                true, "http://gateway/submit", "wx_h5,alipay_h5", "APP_PAY_CORE", "secret", restTemplate);

        assertTrue(adapter.supports("WX_H5"));
        assertFalse(adapter.supports("offline_bank"));
        assertFalse(new GatewayAccessPaymentChannelSubmitAdapter(
                true, "http://gateway/submit", "wx_h5", "APP_PAY_CORE", "", restTemplate)
                .supports("wx_h5"));
    }

    @Test
    void shouldReturnGatewayAcceptanceWithMinimalPayload() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentChannelSubmitResultDTO data = new PaymentChannelSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse response =
                new GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PaymentChannelSubmitResultDTO result = adapter(restTemplate).submit(request());

        assertEquals("WX-1001", result.getChannelTransactionNo());
        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertTrue(result.getResponsePayload().contains("\"source\":\"gateway-access\""));
        assertFalse(result.getResponsePayload().contains("secret"));
    }

    @Test
    void shouldRejectGatewayBusinessFailure() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse response =
                new GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse();
        response.setCode("GATEWAY_ACCESS_DENIED");
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> adapter(restTemplate).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
    }

    private GatewayAccessPaymentChannelSubmitAdapter adapter(RestTemplate restTemplate) {
        return new GatewayAccessPaymentChannelSubmitAdapter(
                true, "http://gateway/submit", "wx_h5", "APP_PAY_CORE", "secret", restTemplate);
    }

    private PaymentChannelSubmitRequestDTO request() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("wx_h5");
        return request;
    }
}
