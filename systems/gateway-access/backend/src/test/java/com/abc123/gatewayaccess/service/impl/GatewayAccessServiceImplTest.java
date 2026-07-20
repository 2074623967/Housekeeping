package com.abc123.gatewayaccess.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import org.junit.jupiter.api.Test;

/**
 * 网关接入服务测试。
 */
class GatewayAccessServiceImplTest {

    private final GatewayAccessServiceImpl service = new GatewayAccessServiceImpl();

    @Test
    void shouldProvideSummaryMetrics() {
        GatewayAccessSummaryDTO summary = service.summary();
        assertEquals(4, summary.getMetrics().size());
    }

    @Test
    void shouldToggleGatewayStatus() {
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setConfigCode("GW_BANK");
        request.setEnabled(true);
        GatewayAccessSummaryDTO summary = service.toggleGateway(request);
        assertEquals(4, summary.getMetrics().size());
    }

    @Test
    void shouldRejectMissingAppCode() {
        assertThrows(IllegalArgumentException.class, () -> service.toggleApplication(new ToggleRequestDTO()));
    }
}
