package com.abc123.gatewayaccess.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelQueryDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
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
    void shouldRejectMissingAppCode() {
        assertThrows(IllegalArgumentException.class, () -> new GatewayAccessServiceImpl(gatewayAccessMapper).toggleApplication(new ToggleRequestDTO()));
    }
}
