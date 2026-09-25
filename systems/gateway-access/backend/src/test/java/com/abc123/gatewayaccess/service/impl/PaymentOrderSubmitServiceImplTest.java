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
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentSubmitResponseDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestTemplate;

class PaymentOrderSubmitServiceImplTest {

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
        channel.setPaymentSubmitUrl("https://channel.example/payments/submit");
        properties.getChannels().put("wx_h5", channel);
        accessService = new GatewayTransactionAccessService(mapper, properties);
    }

    @Test
    void shouldFailClosedWhenSubmitUpstreamIsNotConfigured() {
        properties.getChannels().get("wx_h5").setPaymentSubmitUrl("");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_NOT_CONFIGURED", exception.getCode());
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("FAILED", captor.getValue().getResultStatus());
        assertEquals("danger", captor.getValue().getResultStatusType());
    }

    @Test
    void shouldReturnOnlyDefinitiveAcceptanceAndAudit() {
        configureEnabledGovernance();
        PaymentOrderSubmitResultDTO data = new PaymentOrderSubmitResultDTO();
        data.setChannelTransactionNo(" WX-1001 ");
        data.setAttemptStatus(" 处理中 ");
        data.setAttemptStatusType(" INFO ");
        data.setChannelReturnCode(" USERPAYING ");
        data.setNativePaymentParams(nativePaymentParams());
        UpstreamPaymentSubmitResponseDTO response = new UpstreamPaymentSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(
                eq("https://channel.example/payments/submit"),
                any(PaymentOrderSubmitRequestDTO.class),
                eq(UpstreamPaymentSubmitResponseDTO.class))).thenReturn(response);

        PaymentOrderSubmitRequestDTO miniProgramRequest = request();
        miniProgramRequest.setTerminal("MINI_PROGRAM");
        PaymentOrderSubmitResultDTO result = service().submit(
                "APP_PAY_CORE", "secret", "127.0.0.1", miniProgramRequest);

        assertEquals("WX-1001", result.getChannelTransactionNo());
        assertEquals("处理中", result.getAttemptStatus());
        assertEquals("info", result.getAttemptStatusType());
        assertEquals("USERPAYING", result.getChannelReturnCode());
        assertEquals("prepay_id=wx-prepay", result.getNativePaymentParams().get("package"));
        ArgumentCaptor<GatewayAuditLogEntity> captor = ArgumentCaptor.forClass(GatewayAuditLogEntity.class);
        verify(mapper).insertAuditLog(captor.capture());
        assertEquals("PAYMENT_SUBMIT", captor.getValue().getOperationType());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
    }

    @Test
    void shouldRejectAcceptanceWithoutChannelTransactionNumber() {
        configureEnabledGovernance();
        PaymentOrderSubmitResultDTO data = new PaymentOrderSubmitResultDTO();
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        UpstreamPaymentSubmitResponseDTO response = new UpstreamPaymentSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentSubmitResponseDTO.class)))
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
    void shouldRejectNativePaymentParamsForNonMiniProgramTerminal() {
        configureEnabledGovernance();
        PaymentOrderSubmitResultDTO data = new PaymentOrderSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        data.setNativePaymentParams(nativePaymentParams());
        UpstreamPaymentSubmitResponseDTO response = new UpstreamPaymentSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
    }

    @Test
    void shouldRejectPartialNativePaymentParamsFromUpstream() {
        configureEnabledGovernance();
        PaymentOrderSubmitResultDTO data = new PaymentOrderSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timeStamp", "1710000000");
        data.setNativePaymentParams(params);
        UpstreamPaymentSubmitResponseDTO response = new UpstreamPaymentSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
    }

    @Test
    void shouldAuditUnknownWhenPaymentSubmitTimesOutAfterCallingUpstream() {
        configureEnabledGovernance();
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentSubmitResponseDTO.class)))
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
    void shouldRejectControlCharacterBeforeTrimmingRequestFields() {
        PaymentOrderSubmitRequestDTO request = request();
        request.setOrderNo("ORD-1001\n");

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request));

        assertEquals("GATEWAY_INVALID_ARGUMENT", exception.getCode());
    }

    @Test
    void shouldRejectControlCharacterBeforeTrimmingUpstreamFields() {
        configureEnabledGovernance();
        PaymentOrderSubmitResultDTO data = new PaymentOrderSubmitResultDTO();
        data.setChannelTransactionNo("WX-1001\n");
        data.setAttemptStatus("处理中");
        data.setAttemptStatusType("info");
        data.setChannelReturnCode("USERPAYING");
        UpstreamPaymentSubmitResponseDTO response = new UpstreamPaymentSubmitResponseDTO();
        response.setCode("0");
        response.setData(data);
        when(restTemplate.postForObject(any(String.class), any(), eq(UpstreamPaymentSubmitResponseDTO.class)))
                .thenReturn(response);

        GatewayAccessException exception = assertThrows(GatewayAccessException.class,
                () -> service().submit("APP_PAY_CORE", "secret", "127.0.0.1", request()));

        assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE", exception.getCode());
    }

    private PaymentOrderSubmitServiceImpl service() {
        return new PaymentOrderSubmitServiceImpl(accessService, restTemplate);
    }

    private PaymentOrderSubmitRequestDTO request() {
        PaymentOrderSubmitRequestDTO request = new PaymentOrderSubmitRequestDTO();
        request.setPaymentOrderId("PAY-1001");
        request.setPrepayOrderNo("PRE-1001");
        request.setOrderNo("ORD-1001");
        request.setPayScene("HOME_SERVICE");
        request.setCustomerName("Customer");
        request.setAmount(new BigDecimal("168.00"));
        request.setPaymentMethod("wechat");
        request.setSourceAppId("APP-H5");
        request.setRequestedChannelCode("WX_H5");
        request.setResolvedChannelCode("WX_H5");
        request.setTerminal("H5");
        request.setClientIp("127.0.0.1");
        request.setIdempotencyKey("idem-1001");
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

    private Map<String, String> nativePaymentParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timeStamp", "1710000000");
        params.put("nonceStr", "nonce");
        params.put("package", "prepay_id=wx-prepay");
        params.put("signType", "RSA");
        params.put("paySign", "signature");
        return params;
    }
}
