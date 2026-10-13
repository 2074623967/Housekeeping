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
import com.abc123.gatewayaccess.dto.RefundOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamRefundSubmitResponseDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

class RefundOrderSubmitServiceImplTest {

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
        channel.setRefundSubmitUrl("https://channel.example/refunds/submit");
        properties.getChannels().put("wx_h5", channel);
        accessService = new GatewayTransactionAccessService(mapper, properties);
    }

    @Test
    void shouldFailClosedWhenSubmitUpstreamIsNotConfigured() {
        properties.getChannels().get("wx_h5").setRefundSubmitUrl("");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_NOT_CONFIGURED", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("FAILED", captor.getValue().getResultStatus());
        assertEquals("danger", captor.getValue().getResultStatusType());
    }

    @Test
    void shouldRejectAcceptanceFromAnotherRefundAttempt() {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setRefundOrderId("REF-1001");
        data.setRefundAttemptNo("RFA-OTHER");
        data.setChannelRefundNo("WX-R-OTHER");
        data.setStatus("PROCESSING");
        data.setStatusType("warn");
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getResultStatus());
    }

    @Test
    void shouldRejectAcceptanceWithoutRefundIdentity() {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setChannelRefundNo("WX-R-1001");
        data.setStatus("PROCESSING");
        data.setStatusType("warn");
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getResultStatus());
    }

    @Test
    void shouldReturnOnlyDefinitiveAcceptanceAndAudit() {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setRefundOrderId("REF-1001");
        data.setRefundAttemptNo("RFA-1001");
        data.setChannelRefundNo(" WX-R-1001 ");
        data.setStatus(" processing ");
        data.setStatusType(" WARN ");
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        ArgumentCaptor<RefundOrderSubmitRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(RefundOrderSubmitRequestDTO.class);
        when(restTemplate.postForObject(
                eq("https://channel.example/refunds/submit"),
                requestCaptor.capture(),
                eq(UpstreamRefundSubmitResponseDTO.class))).thenReturn(response);

        RefundOrderSubmitResultDTO result = service().submit(
                "APP_PAY_CORE", "secret", "127.0.0.1", request());

        assertEquals("WX-R-1001", result.getChannelRefundNo());
        assertEquals("PROCESSING", result.getStatus());
        assertEquals("warn", result.getStatusType());
        assertEquals("RFA-1001", requestCaptor.getValue().getRefundAttemptNo());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("REFUND_SUBMIT", captor.getValue().getOperationType());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
    }

    @Test
    void shouldAcceptMatchingSuccessStatusAndStyle() {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setRefundOrderId("REF-1001");
        data.setRefundAttemptNo("RFA-1001");
        data.setChannelRefundNo("WX-R-1001");
        data.setStatus(" success ");
        data.setStatusType(" SUCCESS ");
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenReturn(response);

        RefundOrderSubmitResultDTO result = service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request());

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("success", result.getStatusType());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
    }

    @Test
    void shouldRejectAcceptanceWithoutChannelRefundNumber() {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setStatus("PROCESSING");
        data.setStatusType("warn");
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getResultStatus());
        assertEquals("warn", captor.getValue().getResultStatusType());
    }

    @Test
    void shouldRejectProcessingWithSuccessStyleAsUnknown() {
        assertInvalidAcceptanceStatus("PROCESSING", "success");
    }

    @Test
    void shouldRejectSuccessWithWarningStyleAsUnknown() {
        assertInvalidAcceptanceStatus("SUCCESS", "warn");
    }

    @Test
    void shouldAuditUnknownWhenRefundSubmitTimesOutAfterCallingUpstream() {
        configureEnabledGovernance();
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenThrow(new IllegalStateException("timeout"));

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_UNAVAILABLE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getResultStatus());
        assertEquals("warn", captor.getValue().getResultStatusType());
    }

    @Test
    void shouldRejectInvalidAmountBeforeAuthorization() {
        RefundOrderSubmitRequestDTO request = request();
        request.setRefundAmount(new BigDecimal("1.001"));

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    @Test
    void shouldRejectRawOversizedRefundMethodBeforeAuthorization() {
        RefundOrderSubmitRequestDTO request = request();
        request.setRefundMethod(String.join("", Collections.nCopies(64, "M")) + " ");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    @Test
    void shouldRejectExplicitControlCharactersInOptionalCustomerName() {
        RefundOrderSubmitRequestDTO request = request();
        request.setCustomerName("\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    @Test
    void shouldRejectExplicitControlCharactersInOptionalRefundReason() {
        RefundOrderSubmitRequestDTO request = request();
        request.setRefundReason("\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    @Test
    void shouldRejectRawOversizedOptionalCustomerNameBeforeAuthorization() {
        RefundOrderSubmitRequestDTO request = request();
        request.setCustomerName(String.join("", Collections.nCopies(128, " ")) + " ");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    private RefundOrderSubmitServiceImpl service() {
        return new RefundOrderSubmitServiceImpl(accessService, restTemplate);
    }

    private void assertInvalidAcceptanceStatus(String status, String statusType) {
        configureEnabledGovernance();
        RefundOrderSubmitResultDTO data = new RefundOrderSubmitResultDTO();
        data.setRefundOrderId("REF-1001");
        data.setRefundAttemptNo("RFA-1001");
        data.setChannelRefundNo("WX-R-1001");
        data.setStatus(status);
        data.setStatusType(statusType);
        UpstreamRefundSubmitResponseDTO response = new UpstreamRefundSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamRefundSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("UNKNOWN", captor.getValue().getResultStatus());
        assertEquals("warn", captor.getValue().getResultStatusType());
    }

    private RefundOrderSubmitRequestDTO request() {
        RefundOrderSubmitRequestDTO request = new RefundOrderSubmitRequestDTO();
        request.setRefundOrderId("REF-1001");
        request.setRefundAttemptNo("RFA-1001");
        request.setPaymentOrderId("PAY-1001");
        request.setOrderNo("ORD-1001");
        request.setCustomerName("Customer");
        request.setRefundAmount(new BigDecimal("68.00"));
        request.setRefundMethod("ORIGINAL_ROUTE");
        request.setRefundReason("customer request");
        request.setChannelCode("WX_H5");
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
