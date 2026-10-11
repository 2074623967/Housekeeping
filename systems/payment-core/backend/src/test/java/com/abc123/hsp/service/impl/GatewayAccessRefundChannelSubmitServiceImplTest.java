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

import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

class GatewayAccessRefundChannelSubmitServiceImplTest {

    @Test
    void shouldExchangeGatewayRefundContractOverLoopbackHttp() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicReference<JsonNode> submittedBody = new AtomicReference<>();
        AtomicReference<String> submittedAppCode = new AtomicReference<>();
        AtomicReference<String> submittedAccessKey = new AtomicReference<>();
        AtomicInteger requestCount = new AtomicInteger();
        AtomicBoolean accepted = new AtomicBoolean(true);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/gateway/refunds/submit", exchange -> {
            requestCount.incrementAndGet();
            submittedAppCode.set(exchange.getRequestHeaders().getFirst("X-Gateway-App-Code"));
            submittedAccessKey.set(exchange.getRequestHeaders().getFirst("X-Gateway-Access-Key"));
            submittedBody.set(objectMapper.readTree(exchange.getRequestBody()));
            String response = accepted.get()
                    ? "{\"code\":\"0\",\"message\":\"success\",\"data\":{"
                            + "\"channelRefundNo\":\"GW-REF-001\",\"status\":\"PROCESSING\","
                            + "\"statusType\":\"warn\"},\"requestId\":\"LOCAL-TEST\"}"
                    : "{\"code\":\"GATEWAY_UPSTREAM_NOT_CONFIGURED\","
                            + "\"message\":\"upstream unavailable\",\"data\":null}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2000);
            factory.setReadTimeout(2000);
            GatewayAccessRefundChannelSubmitServiceImpl adapter =
                    new GatewayAccessRefundChannelSubmitServiceImpl(
                            true, "http://127.0.0.1:" + server.getAddress().getPort()
                                    + "/api/gateway/refunds/submit",
                            "wx_h5", "APP_PAY_CORE", "local-test-key", new RestTemplate(factory));

            RefundChannelSubmitResultDTO result = adapter.submit(request());
            assertEquals("GW-REF-001", result.getChannelRefundNo());
            assertEquals("PROCESSING", result.getStatus());
            assertEquals("warn", result.getStatusType());
            assertEquals("APP_PAY_CORE", submittedAppCode.get());
            assertEquals("local-test-key", submittedAccessKey.get());
            assertEquals("REF-1001", submittedBody.get().path("refundOrderId").asText());
            assertEquals("RFA-1001", submittedBody.get().path("refundAttemptNo").asText());
            assertEquals("PAY-1001", submittedBody.get().path("paymentOrderId").asText());
            assertEquals("ORD-1001", submittedBody.get().path("orderNo").asText());
            assertEquals("wx_h5", submittedBody.get().path("channelCode").asText());
            assertEquals(0, submittedBody.get().path("refundAmount").decimalValue()
                    .compareTo(new BigDecimal("10.00")));
            assertFalse(submittedBody.get().toString().contains("local-test-key"));

            accepted.set(false);
            RefundChannelSubmitException rejected = assertThrows(RefundChannelSubmitException.class,
                    () -> adapter.submit(request()));
            assertFalse(rejected.isResultUnknown());
            assertEquals(2, requestCount.get());
        } finally {
            server.stop(0);
        }
    }

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
    void shouldRejectInvalidRequestBeforeGatewayCall() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        GatewayAccessRefundChannelSubmitServiceImpl adapter = adapter(restTemplate);

        assertInvalidRequest(assertThrows(RefundChannelSubmitException.class, () -> adapter.submit(null)));

        RefundChannelSubmitRequestDTO missingRequired = request();
        missingRequired.setPaymentOrderId("   ");
        assertInvalidRequest(assertThrows(
                RefundChannelSubmitException.class, () -> adapter.submit(missingRequired)));

        RefundChannelSubmitRequestDTO invalidOptional = request();
        invalidOptional.setRefundReason("customer\nrequest");
        assertInvalidRequest(assertThrows(
                RefundChannelSubmitException.class, () -> adapter.submit(invalidOptional)));

        RefundChannelSubmitRequestDTO oversizedOptional = request();
        oversizedOptional.setRefundReason(repeat(" ", 256));
        assertInvalidRequest(assertThrows(
                RefundChannelSubmitException.class, () -> adapter.submit(oversizedOptional)));

        RefundChannelSubmitRequestDTO oversized = request();
        oversized.setRefundMethod(repeat("M", 32) + " ");
        assertInvalidRequest(assertThrows(
                RefundChannelSubmitException.class, () -> adapter.submit(oversized)));

        BigDecimal[] invalidAmounts = {
                null,
                BigDecimal.ZERO,
                new BigDecimal("1.001"),
                new BigDecimal("10000000000000000.00")
        };
        for (BigDecimal invalidAmount : invalidAmounts) {
            RefundChannelSubmitRequestDTO invalidAmountRequest = request();
            invalidAmountRequest.setRefundAmount(invalidAmount);
            assertInvalidRequest(assertThrows(
                    RefundChannelSubmitException.class, () -> adapter.submit(invalidAmountRequest)));
        }

        verify(restTemplate, never()).exchange(
                any(String.class), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class));
    }

    @Test
    void shouldSendNormalizedContractRequestWithoutMutatingSource() {
        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        AtomicReference<GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitRequest> sentRequest =
                new AtomicReference<>();
        stubSuccess(restTemplate, sentRequest);
        RefundChannelSubmitRequestDTO request = request();
        request.setRefundOrderId(" REF-1001 ");
        request.setRefundAttemptNo(" RFA-1001 ");
        request.setCustomerName("   ");
        request.setRefundReason(" customer request ");
        request.setChannelCode(" wx_h5 ");
        request.setRefundStatus("PROCESSING");
        request.setSubmittedChannelCode("internal-channel");
        request.setChannelRefundNo("internal-refund-no");

        RefundChannelSubmitResultDTO result = adapter(restTemplate).submit(request);

        GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitRequest sent = sentRequest.get();
        assertNotNull(sent);
        assertEquals("REF-1001", sent.getRefundOrderId());
        assertEquals("RFA-1001", sent.getRefundAttemptNo());
        assertNull(sent.getCustomerName());
        assertEquals("customer request", sent.getRefundReason());
        assertEquals("wx_h5", sent.getChannelCode());
        assertTrue(result.getResponsePayload().contains("\"channelCode\":\"wx_h5\""));
        assertEquals(" REF-1001 ", request.getRefundOrderId());
        assertEquals(" RFA-1001 ", request.getRefundAttemptNo());
        assertEquals("   ", request.getCustomerName());
        assertEquals(" wx_h5 ", request.getChannelCode());
        assertEquals("internal-refund-no", request.getChannelRefundNo());
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

    @Test
    void shouldRejectRefundAcceptanceWithMismatchedStatusStyle() {
        for (String[] facts : new String[][] {
            {"PROCESSING", "success"},
            {"SUCCESS", "warn"},
            {"FAIL", "info"}
        }) {
            RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
            RefundChannelSubmitResultDTO data = new RefundChannelSubmitResultDTO();
            data.setChannelRefundNo("WX-R-1001");
            data.setStatus(facts[0]);
            data.setStatusType(facts[1]);
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
        request.setRefundAttemptNo("RFA-1001");
        request.setPaymentOrderId("PAY-1001");
        request.setOrderNo("ORD-1001");
        request.setCustomerName("Customer");
        request.setRefundAmount(new BigDecimal("10.00"));
        request.setRefundMethod("ORIGINAL_ROUTE");
        request.setRefundReason("customer request");
        request.setChannelCode("wx_h5");
        return request;
    }

    private void stubSuccess(
            RestTemplate restTemplate,
            AtomicReference<GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitRequest> sentRequest) {
        RefundChannelSubmitResultDTO data = new RefundChannelSubmitResultDTO();
        data.setChannelRefundNo("WX-R-1001");
        data.setStatus("PROCESSING");
        data.setStatusType("warn");
        GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse response =
                new GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.exchange(
                eq("http://gateway/submit"), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitResponse.class)))
                .thenAnswer(invocation -> {
                    HttpEntity<GatewayAccessRefundChannelSubmitServiceImpl.GatewayRefundSubmitRequest> entity =
                            invocation.getArgument(2);
                    sentRequest.set(entity.getBody());
                    return ResponseEntity.ok(response);
                });
    }

    private void assertInvalidRequest(RefundChannelSubmitException exception) {
        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_FAILED, exception.getCode());
        assertEquals("gateway-access refund submit request is invalid", exception.getMessage());
        assertFalse(exception.isResultUnknown());
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
