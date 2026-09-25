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
import com.abc123.gatewayaccess.dto.RefundOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamRefundQueryResponseDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

class RefundOrderQueryServiceImplTest {

    private GatewayAccessMapper mapper;
    private RestTemplate restTemplate;
    private GatewayTransactionProperties properties;
    private GatewayTransactionAccessService accessService;

    @BeforeEach
    void setUp() {
        mapper = mock(GatewayAccessMapper.class);
        restTemplate = mock(RestTemplate.class);
        properties = new GatewayTransactionProperties();
        properties.setAccessKey("secret");
        GatewayTransactionProperties.Channel channel = new GatewayTransactionProperties.Channel();
        channel.setGatewayCode("GW_WX");
        channel.setRefundQueryUrl("https://channel.example/refunds/query");
        properties.getChannels().put("wx_h5", channel);
        accessService = new GatewayTransactionAccessService(mapper, properties);
    }

    @Test
    void shouldFailClosedWhenRefundUpstreamIsNotConfigured() {
        properties.getChannels().get("wx_h5").setRefundQueryUrl("");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_NOT_CONFIGURED", exception.getCode());
        verify(mapper).insertAuditLog(any(GatewayAuditLogEntity.class));
    }

    @Test
    void shouldReturnDefinitiveMatchingRefundFactAndAudit() {
        configureEnabledGovernance();
        RefundOrderQueryResultDTO data = new RefundOrderQueryResultDTO();
        data.setRefundStatus(" success ");
        data.setChannelRefundNo(" CHANNEL-REF-001 ");
        UpstreamRefundQueryResponseDTO response = new UpstreamRefundQueryResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(
                eq("https://channel.example/refunds/query"),
                any(RefundOrderQueryRequestDTO.class),
                eq(UpstreamRefundQueryResponseDTO.class))).thenReturn(response);

        RefundOrderQueryResultDTO result = service().query(
                "APP_PAY_CORE", "secret", "127.0.0.1", request());

        assertEquals("SUCCESS", result.getRefundStatus());
        assertEquals("CHANNEL-REF-001", result.getChannelRefundNo());
        assertEquals("GATEWAY_ACCESS_PROXY", result.getQuerySource());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("REFUND_QUERY", captor.getValue().getOperationType());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
    }

    @Test
    void shouldQueryByPlatformRefundOrderWhenChannelRefundNumberIsUnknown() {
        configureEnabledGovernance();
        RefundOrderQueryResultDTO data = new RefundOrderQueryResultDTO();
        data.setRefundStatus("PROCESSING");
        data.setChannelRefundNo("CHANNEL-REF-RECOVERED");
        UpstreamRefundQueryResponseDTO response = new UpstreamRefundQueryResponseDTO();
        response.setCode("0");
        response.setData(data);
        ArgumentCaptor<RefundOrderQueryRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RefundOrderQueryRequestDTO.class);
        when(restTemplate.postForObject(
                eq("https://channel.example/refunds/query"),
                requestCaptor.capture(),
                eq(UpstreamRefundQueryResponseDTO.class))).thenReturn(response);
        RefundOrderQueryRequestDTO request = request();
        request.setChannelRefundNo(null);

        RefundOrderQueryResultDTO result = service().query(
                "APP_PAY_CORE", "secret", "127.0.0.1", request);

        assertEquals("REF-001", requestCaptor.getValue().getRefundOrderId());
        assertEquals("RFA-001", requestCaptor.getValue().getRefundAttemptNo());
        assertEquals(null, requestCaptor.getValue().getChannelRefundNo());
        assertEquals("CHANNEL-REF-RECOVERED", result.getChannelRefundNo());
    }

    @Test
    void shouldRejectMismatchedChannelRefundNumber() {
        configureEnabledGovernance();
        RefundOrderQueryResultDTO data = new RefundOrderQueryResultDTO();
        data.setRefundStatus("PROCESSING");
        data.setChannelRefundNo("CHANNEL-REF-OTHER");
        UpstreamRefundQueryResponseDTO response = new UpstreamRefundQueryResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundQueryResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
    }

    @Test
    void shouldRejectRequestControlCharactersBeforeAuthorization() {
        RefundOrderQueryRequestDTO request = request();
        request.setRefundOrderId("REF-001\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper).insertAuditLog(any(GatewayAuditLogEntity.class));
    }

    @Test
    void shouldRejectControlCharactersInOptionalChannelRefundNumber() {
        RefundOrderQueryRequestDTO request = request();
        request.setChannelRefundNo("\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper).insertAuditLog(any(GatewayAuditLogEntity.class));
    }

    @Test
    void shouldRejectRawOversizedRefundOrderNumberBeforeAuthorization() {
        RefundOrderQueryRequestDTO request = request();
        request.setRefundOrderId(String.join("", Collections.nCopies(64, "R")) + " ");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().query("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
        verify(mapper).insertAuditLog(any(GatewayAuditLogEntity.class));
    }

    private RefundOrderQueryServiceImpl service() {
        return new RefundOrderQueryServiceImpl(accessService, restTemplate);
    }

    private RefundOrderQueryRequestDTO request() {
        RefundOrderQueryRequestDTO request = new RefundOrderQueryRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setRefundAttemptNo("RFA-001");
        request.setChannelCode("WX_H5");
        request.setChannelRefundNo("CHANNEL-REF-001");
        return request;
    }

    private void configureEnabledGovernance() {
        GatewayAppEntity application = new GatewayAppEntity();
        application.setStatus("ENABLED");
        when(mapper.findApplicationByCode("APP_PAY_CORE")).thenReturn(application);

        GatewayPermissionEntity permission = new GatewayPermissionEntity();
        permission.setAppCode("APP_PAY_CORE");
        permission.setStatus("ENABLED");
        when(mapper.findPermissionByCode("PERM_PAYMENT_CORE")).thenReturn(permission);

        GatewayChannelEntity gateway = new GatewayChannelEntity();
        gateway.setStatus("ENABLED");
        gateway.setSignAlgorithm("HMAC-SHA256");
        when(mapper.findGatewayByCode("GW_WX")).thenReturn(gateway);

        GatewayCertificateEntity certificate = new GatewayCertificateEntity();
        certificate.setExpireAt(LocalDate.now().plusDays(30).toString());
        when(mapper.findEnabledCertificateByGatewayCode("GW_WX")).thenReturn(certificate);

        GatewayReleaseRouteDTO route = new GatewayReleaseRouteDTO();
        route.setTrafficPercent(100);
        when(mapper.findEnabledReleaseRoute("GW_WX", "PROD")).thenReturn(route);
    }
}
