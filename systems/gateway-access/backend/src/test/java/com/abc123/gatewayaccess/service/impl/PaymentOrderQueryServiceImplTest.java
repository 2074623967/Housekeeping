package com.abc123.gatewayaccess.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentQueryResponseDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

class PaymentOrderQueryServiceImplTest {

    private GatewayAccessMapper mapper;
    private RestTemplate restTemplate;
    private GatewayTransactionProperties properties;

    @BeforeEach
    void setUp() {
        mapper = mock(GatewayAccessMapper.class);
        restTemplate = mock(RestTemplate.class);
        properties = new GatewayTransactionProperties();
        properties.setAccessKey("secret");
        GatewayTransactionProperties.Channel channel = new GatewayTransactionProperties.Channel();
        channel.setGatewayCode("GW_WX");
        channel.setPaymentQueryUrl("https://channel.example/payments/query");
        properties.getChannels().put("wx_h5", channel);
    }

    @Test
    void shouldFailClosedWhenCredentialIsNotConfigured() {
        properties.setAccessKey("");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "", "127.0.0.1", request()));

        assertEquals("GATEWAY_ACCESS_NOT_CONFIGURED", exception.getCode());
        verify(mapper).insertAuditLog(any(GatewayAuditLogEntity.class));
    }

    @Test
    void shouldRejectWhenReleaseRouteIsUnavailable() {
        configureEnabledGovernance(false);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_ROUTE_DISABLED", exception.getCode());
    }

    @Test
    void shouldReturnOnlyDefinitiveUpstreamTradeFactAndAudit() {
        configureEnabledGovernance(true);
        PaymentOrderQueryResultDTO data = new PaymentOrderQueryResultDTO();
        data.setTradeStatus(" success ");
        data.setChannelTransactionNo(" WX-1001 ");
        UpstreamPaymentQueryResponseDTO response = new UpstreamPaymentQueryResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(
                eq("https://channel.example/payments/query"),
                any(PaymentOrderQueryRequestDTO.class),
                eq(UpstreamPaymentQueryResponseDTO.class))).thenReturn(response);

        PaymentOrderQueryResultDTO result = service().query(
                "APP_PAY_CORE", "secret", "127.0.0.1", request());

        assertEquals("SUCCESS", result.getTradeStatus());
        assertEquals("WX-1001", result.getChannelTransactionNo());
        assertEquals("GATEWAY_ACCESS_PROXY", result.getQuerySource());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
        assertEquals("PAYMENT_QUERY", captor.getValue().getOperationType());
    }

    @Test
    void shouldRejectNonDefinitiveUpstreamResponse() {
        configureEnabledGovernance(true);
        UpstreamPaymentQueryResponseDTO response = new UpstreamPaymentQueryResponseDTO();
        response.setCode("0");
        response.setData(new PaymentOrderQueryResultDTO());
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentQueryResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
    }

    @Test
    void shouldRejectUnknownStatusWithoutSuccessfulAudit() {
        configureEnabledGovernance(true);
        PaymentOrderQueryResultDTO data = new PaymentOrderQueryResultDTO();
        data.setTradeStatus("PAID");
        stubUpstream(data);

        assertInvalidUpstreamResponse();
    }

    @Test
    void shouldRejectOversizedOrControlledUpstreamFieldsBeforeSuccessfulAudit() {
        configureEnabledGovernance(true);
        PaymentOrderQueryResultDTO data = new PaymentOrderQueryResultDTO();
        data.setTradeStatus("SUCCESS");
        data.setChannelTransactionNo(repeat("W", 129));
        stubUpstream(data);
        assertInvalidUpstreamResponse();

        data.setChannelTransactionNo("WX-1001\n");
        assertInvalidUpstreamResponse();

        data.setChannelTransactionNo("WX-1001");
        data.setStandardizedMessage(repeat("M", 256) + " ");
        assertInvalidUpstreamResponse();
    }

    private void stubUpstream(PaymentOrderQueryResultDTO data) {
        UpstreamPaymentQueryResponseDTO response = new UpstreamPaymentQueryResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentQueryResponseDTO.class)))
                .thenReturn(response);
    }

    private void assertInvalidUpstreamResponse() {
        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request()));
        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper, org.mockito.Mockito.atLeastOnce()).insertAuditLog(captor.capture());
        for (GatewayAuditLogEntity audit : captor.getAllValues()) {
            assertEquals("FAILED", audit.getResultStatus());
        }
    }

    @Test
    void shouldRejectOversizedQueryFieldsBeforeAuthorization() {
        PaymentOrderQueryRequestDTO oversized = request();
        oversized.setPaymentOrderId(repeat("P", 65));

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", oversized));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper, org.mockito.Mockito.never()).findApplicationByCode(any(String.class));
    }

    @Test
    void shouldRejectControlCharactersBeforeAuthorization() {
        PaymentOrderQueryRequestDTO invalid = request();
        invalid.setChannelTransactionNo("WX-1001\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", invalid));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper, org.mockito.Mockito.never()).findApplicationByCode(any(String.class));
    }

    @Test
    void shouldRejectWhitespaceOnlyControlCharactersBeforeAuthorization() {
        PaymentOrderQueryRequestDTO invalid = request();
        invalid.setChannelTransactionNo("\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", invalid));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper, org.mockito.Mockito.never()).findApplicationByCode(any(String.class));
    }

    @Test
    void shouldRejectOversizedWhitespaceOnlyChannelTransactionNo() {
        PaymentOrderQueryRequestDTO invalid = request();
        invalid.setChannelTransactionNo(repeat(" ", 129));

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", invalid));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper, org.mockito.Mockito.never()).findApplicationByCode(any(String.class));
    }

    private PaymentOrderQueryServiceImpl service() {
        return new PaymentOrderQueryServiceImpl(mapper, properties, restTemplate);
    }

    private PaymentOrderQueryRequestDTO request() {
        PaymentOrderQueryRequestDTO request = new PaymentOrderQueryRequestDTO();
        request.setPaymentOrderId("PAY-1001");
        request.setOrderNo("ORD-1001");
        request.setChannelCode("WX_H5");
        return request;
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private void configureEnabledGovernance(boolean withRoute) {
        GatewayAppEntity application = new GatewayAppEntity();
        application.setStatus("ENABLED");
        when(mapper.findApplicationByCode("APP_PAY_CORE")).thenReturn(application);

        GatewayPermissionEntity permission = new GatewayPermissionEntity();
        permission.setAppCode("APP_PAY_CORE");
        permission.setStatus("ENABLED");
        when(mapper.findPermissionByCode("PERM_PAYMENT_CORE")).thenReturn(permission);

        GatewayChannelEntity gateway = new GatewayChannelEntity();
        gateway.setGatewayCode("GW_WX");
        gateway.setStatus("ENABLED");
        gateway.setSignAlgorithm("HMAC-SHA256");
        when(mapper.findGatewayByCode("GW_WX")).thenReturn(gateway);

        GatewayCertificateEntity certificate = new GatewayCertificateEntity();
        certificate.setExpireAt(LocalDate.now().plusDays(30).toString());
        when(mapper.findEnabledCertificateByGatewayCode("GW_WX")).thenReturn(certificate);

        if (withRoute) {
            GatewayReleaseRouteDTO route = new GatewayReleaseRouteDTO();
            route.setTrafficPercent(100);
            when(mapper.findEnabledReleaseRoute("GW_WX", "PROD")).thenReturn(route);
        }
    }
}
