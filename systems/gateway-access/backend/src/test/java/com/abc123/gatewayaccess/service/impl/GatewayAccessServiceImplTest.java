package com.abc123.gatewayaccess.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayAuditLogDTO;
import com.abc123.gatewayaccess.dto.GatewayAuditQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelQueryDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteDTO;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteQueryDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 网关接入服务测试。
 */
@ExtendWith(MockitoExtension.class)
class GatewayAccessServiceImplTest {

    @Mock
    private GatewayAccessMapper gatewayAccessMapper;

    @Test
    void shouldProvideSummaryMetrics() {
        when(gatewayAccessMapper.countApplications()).thenReturn(3L);
        when(gatewayAccessMapper.countEnabledGateways()).thenReturn(2L);
        when(gatewayAccessMapper.countEnabledCertificates()).thenReturn(2L);
        when(gatewayAccessMapper.countEnabledPermissions()).thenReturn(2L);

        GatewayAccessSummaryDTO summary = new GatewayAccessServiceImpl(gatewayAccessMapper).summary();
        assertEquals(4, summary.getMetrics().size());
    }

    @Test
    void shouldToggleGatewayStatus() {
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setConfigCode("GW_BANK");
        request.setEnabled(true);
        when(gatewayAccessMapper.updateGatewayStatus("GW_BANK", "ENABLED", "success")).thenReturn(1);
        when(gatewayAccessMapper.countApplications()).thenReturn(3L);
        when(gatewayAccessMapper.countEnabledGateways()).thenReturn(3L);
        when(gatewayAccessMapper.countEnabledCertificates()).thenReturn(2L);
        when(gatewayAccessMapper.countEnabledPermissions()).thenReturn(2L);
        GatewayAccessSummaryDTO summary = new GatewayAccessServiceImpl(gatewayAccessMapper).toggleGateway(request);
        assertEquals(4, summary.getMetrics().size());
        verify(gatewayAccessMapper).updateGatewayStatus("GW_BANK", "ENABLED", "success");
    }

    @Test
    void shouldNormalizeGatewayFilters() {
        GatewayChannelDTO record = new GatewayChannelDTO();
        record.setGatewayCode("GW_WECHAT");
        when(gatewayAccessMapper.findGateways(org.mockito.ArgumentMatchers.any(GatewayChannelQueryDTO.class)))
                .thenReturn(Collections.singletonList(record));

        GatewayChannelQueryDTO query = new GatewayChannelQueryDTO();
        query.setKeyword("  wechat  ");
        query.setChannelType("  WECHAT  ");
        query.setStatus("  ENABLED  ");

        assertEquals(1, new GatewayAccessServiceImpl(gatewayAccessMapper).gateways(query).getRecords().size());

        ArgumentCaptor<GatewayChannelQueryDTO> captor = ArgumentCaptor.forClass(GatewayChannelQueryDTO.class);
        verify(gatewayAccessMapper).findGateways(captor.capture());
        assertEquals("wechat", captor.getValue().getKeyword());
        assertEquals("WECHAT", captor.getValue().getChannelType());
        assertEquals("ENABLED", captor.getValue().getStatus());
    }

    @Test
    void shouldClassifyCertificateRiskByExpireDate() {
        GatewayCertificateDTO certificate = new GatewayCertificateDTO();
        certificate.setCertificateCode("CERT-001");
        certificate.setExpireAt(LocalDate.now().plusDays(5).toString());
        when(gatewayAccessMapper.findCertificates()).thenReturn(Collections.singletonList(certificate));

        GatewayCertificateDTO result = new GatewayAccessServiceImpl(gatewayAccessMapper)
                .certificates("全部")
                .getRecords()
                .get(0);

        assertEquals("7天内到期", result.getRiskLevel());
        assertEquals("danger", result.getRiskLevelType());
        assertEquals(5L, result.getRemainingDays());
    }

    @Test
    void shouldFilterCertificatesByRiskLevel() {
        GatewayCertificateDTO riskCertificate = new GatewayCertificateDTO();
        riskCertificate.setCertificateCode("CERT-001");
        riskCertificate.setExpireAt(LocalDate.now().plusDays(5).toString());
        GatewayCertificateDTO normalCertificate = new GatewayCertificateDTO();
        normalCertificate.setCertificateCode("CERT-002");
        normalCertificate.setExpireAt(LocalDate.now().plusDays(60).toString());
        when(gatewayAccessMapper.findCertificates()).thenReturn(java.util.Arrays.asList(riskCertificate, normalCertificate));

        GatewayAccessServiceImpl service = new GatewayAccessServiceImpl(gatewayAccessMapper);
        assertEquals(1, service.certificates("7天内到期").getRecords().size());
        assertEquals("CERT-001", service.certificates("7天内到期").getRecords().get(0).getCertificateCode());
    }

    @Test
    void shouldRejectMissingAppCode() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayAccessServiceImpl(gatewayAccessMapper).toggleApplication(new ToggleRequestDTO()));
    }

    @Test
    void shouldNormalizeAuditFilters() {
        GatewayAuditLogDTO record = new GatewayAuditLogDTO();
        record.setRequestId("REQ-001");
        when(gatewayAccessMapper.findAuditLogs(org.mockito.ArgumentMatchers.any(GatewayAuditQueryDTO.class)))
                .thenReturn(Collections.singletonList(record));

        GatewayAuditQueryDTO query = new GatewayAuditQueryDTO();
        query.setKeyword("  REQ-001  ");
        query.setAppCode("  APP_PAY_CORE  ");
        query.setResultStatus("  SUCCESS  ");

        assertEquals(1, new GatewayAccessServiceImpl(gatewayAccessMapper).auditLogs(query).getRecords().size());

        ArgumentCaptor<GatewayAuditQueryDTO> captor = ArgumentCaptor.forClass(GatewayAuditQueryDTO.class);
        verify(gatewayAccessMapper).findAuditLogs(captor.capture());
        assertEquals("REQ-001", captor.getValue().getKeyword());
        assertEquals("APP_PAY_CORE", captor.getValue().getAppCode());
        assertEquals("SUCCESS", captor.getValue().getResultStatus());
    }

    @Test
    void shouldNormalizeReleaseRouteFilters() {
        GatewayReleaseRouteDTO record = new GatewayReleaseRouteDTO();
        record.setRouteCode("ROUTE_ALI_GRAY");
        when(gatewayAccessMapper.findReleaseRoutes(org.mockito.ArgumentMatchers.any(GatewayReleaseRouteQueryDTO.class)))
                .thenReturn(Collections.singletonList(record));

        GatewayReleaseRouteQueryDTO query = new GatewayReleaseRouteQueryDTO();
        query.setEnvironment("  GRAY  ");
        query.setStatus("  ENABLED  ");

        assertEquals(1, new GatewayAccessServiceImpl(gatewayAccessMapper).releaseRoutes(query).getRecords().size());

        ArgumentCaptor<GatewayReleaseRouteQueryDTO> captor = ArgumentCaptor.forClass(GatewayReleaseRouteQueryDTO.class);
        verify(gatewayAccessMapper).findReleaseRoutes(captor.capture());
        assertEquals("GRAY", captor.getValue().getEnvironment());
        assertEquals("ENABLED", captor.getValue().getStatus());
    }
}
