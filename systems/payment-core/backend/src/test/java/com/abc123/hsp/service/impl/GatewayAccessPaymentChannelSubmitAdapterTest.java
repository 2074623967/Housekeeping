package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        data.setGatewayCode(" GW_WX ");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        data.setNativePaymentParams(nativePaymentParams());
        GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse response =
                new GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PaymentChannelSubmitRequestDTO miniProgramRequest = request();
        miniProgramRequest.setTerminal("MINI_PROGRAM");
        PaymentChannelSubmitResultDTO result = adapter(restTemplate).submit(miniProgramRequest);

        assertEquals("WX-1001", result.getChannelTransactionNo());
        assertEquals("GW_WX", result.getGatewayCode());
        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertEquals("prepay_id=wx-prepay", result.getNativePaymentParams().get("package"));
        assertTrue(result.getResponsePayload().contains("\"source\":\"gateway-access\""));
        assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"GW_WX\""));
        assertFalse(result.getResponsePayload().contains("secret"));

        data.setGatewayCode(null);
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                assertThrows(BusinessException.class,
                        () -> adapter(restTemplate).submit(miniProgramRequest)).getCode());
        data.setGatewayCode("GW_WX\n");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                assertThrows(BusinessException.class,
                        () -> adapter(restTemplate).submit(miniProgramRequest)).getCode());
    }

    @Test
    void shouldRejectInvalidRequestBeforeGatewayCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessPaymentChannelSubmitAdapter adapter = adapter(restTemplate);

        assertInvalidRequest(assertThrows(BusinessException.class, () -> adapter.submit(null)));

        PaymentChannelSubmitRequestDTO missingRequired = request();
        missingRequired.setPaymentOrderId("   ");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.submit(missingRequired)));

        PaymentChannelSubmitRequestDTO invalidOptional = request();
        invalidOptional.setCustomerName("customer\nname");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.submit(invalidOptional)));

        PaymentChannelSubmitRequestDTO oversizedOptional = request();
        oversizedOptional.setRequestedChannelCode(repeat(" ", 65));
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.submit(oversizedOptional)));

        PaymentChannelSubmitRequestDTO oversized = request();
        oversized.setPaymentMethod(repeat("M", 32) + " ");
        assertInvalidRequest(assertThrows(
                BusinessException.class, () -> adapter.submit(oversized)));

        BigDecimal[] invalidAmounts = {
                null,
                BigDecimal.ZERO,
                new BigDecimal("1.001"),
                new BigDecimal("10000000000000000.00")
        };
        for (BigDecimal invalidAmount : invalidAmounts) {
            PaymentChannelSubmitRequestDTO invalidAmountRequest = request();
            invalidAmountRequest.setAmount(invalidAmount);
            assertInvalidRequest(assertThrows(
                    BusinessException.class, () -> adapter.submit(invalidAmountRequest)));
        }

        verify(restTemplate, never()).exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse.class));
    }

    @Test
    void shouldSendNormalizedRequestCopy() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        AtomicReference<PaymentChannelSubmitRequestDTO> sentRequest = new AtomicReference<>();
        stubSuccess(restTemplate, sentRequest);
        PaymentChannelSubmitRequestDTO request = request();
        request.setPaymentOrderId(" PAY-001 ");
        request.setCustomerName("   ");
        request.setRequestedChannelCode(" WX_H5 ");
        request.setResolvedChannelCode(" wx_h5 ");

        PaymentChannelSubmitResultDTO result = adapter(restTemplate).submit(request);

        PaymentChannelSubmitRequestDTO sent = sentRequest.get();
        assertNotNull(sent);
        assertNotSame(request, sent);
        assertEquals("PAY-001", sent.getPaymentOrderId());
        assertNull(sent.getCustomerName());
        assertEquals("WX_H5", sent.getRequestedChannelCode());
        assertEquals("wx_h5", sent.getResolvedChannelCode());
        assertTrue(result.getResponsePayload().contains("\"channelCode\":\"wx_h5\""));
        assertEquals(" PAY-001 ", request.getPaymentOrderId());
        assertEquals("   ", request.getCustomerName());
        assertEquals(" wx_h5 ", request.getResolvedChannelCode());
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
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        assertEquals(0, exception.getSuppressed().length);
    }

    @Test
    void shouldRejectControlCharacterInGatewayAcceptance() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentChannelSubmitResultDTO data = new PaymentChannelSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001\n");
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

        BusinessException exception = assertThrows(
                BusinessException.class, () -> adapter(restTemplate).submit(request()));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
    }

    @Test
    void shouldRejectNativePaymentParamsForNonMiniProgramTerminal() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        PaymentChannelSubmitResultDTO data = new PaymentChannelSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001");
        data.setGatewayCode("GW_WX");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        data.setNativePaymentParams(nativePaymentParams());
        GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse response =
                new GatewayAccessPaymentChannelSubmitAdapter.GatewaySubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
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
        request.setPaymentOrderId("PAY-001");
        request.setPrepayOrderNo("PREPAY-001");
        request.setOrderNo("ORDER-001");
        request.setPayScene("HOME_SERVICE");
        request.setCustomerName("CUSTOMER-001");
        request.setAmount(new BigDecimal("88.00"));
        request.setPaymentMethod("WECHAT");
        request.setSourceAppId("APP-HOME");
        request.setRequestedChannelCode("wx_h5");
        request.setResolvedChannelCode("wx_h5");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setIdempotencyKey("IDEMPOTENCY-001");
        return request;
    }

    private void stubSuccess(
            RestTemplate restTemplate,
            AtomicReference<PaymentChannelSubmitRequestDTO> sentRequest) {
        PaymentChannelSubmitResultDTO data = new PaymentChannelSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001");
        data.setGatewayCode("GW_WX");
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
                .thenAnswer(invocation -> {
                    HttpEntity<PaymentChannelSubmitRequestDTO> entity = invocation.getArgument(2);
                    sentRequest.set(entity.getBody());
                    return ResponseEntity.ok(response);
                });
    }

    private Map<String, String> nativePaymentParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timeStamp", "1710000000");
        params.put("nonceStr", "nonce");
        params.put("package", "prepay_id=wx-prepay");
        params.put("signType", "RSA");
        params.put("paySign", "signature");
        return params;
    }

    private void assertInvalidRequest(BusinessException exception) {
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("gateway-access payment submit request is invalid", exception.getMessage());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
