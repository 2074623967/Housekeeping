package com.abc123.gatewayaccess.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.service.GatewayAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 网关接入控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class GatewayAccessControllerTest {

    @Mock
    private GatewayAccessService gatewayAccessService;

    @Test
    void shouldReturnSummary() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.summary();

        verify(gatewayAccessService).summary();
    }

    @Test
    void shouldListApplications() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.applications();

        verify(gatewayAccessService).applications();
    }

    @Test
    void shouldListGateways() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.gateways("wechat", "WECHAT", "ENABLED");

        verify(gatewayAccessService).gateways(any());
    }

    @Test
    void shouldListCertificates() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.certificates("7天内到期");

        verify(gatewayAccessService).certificates("7天内到期");
    }

    @Test
    void shouldListPermissions() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.permissions();

        verify(gatewayAccessService).permissions();
    }

    @Test
    void shouldListReleaseRoutes() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.releaseRoutes("GRAY", "ENABLED");

        verify(gatewayAccessService).releaseRoutes(any());
    }

    @Test
    void shouldListAuditLogs() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);

        controller.auditLogs("REQ-001", "APP-PAYMENT", "FAILED");

        verify(gatewayAccessService).auditLogs(any());
    }

    @Test
    void shouldToggleApplication() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);
        ToggleRequestDTO request = new ToggleRequestDTO();

        controller.toggleApplication(request);

        verify(gatewayAccessService).toggleApplication(request);
    }

    @Test
    void shouldToggleGateway() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);
        ToggleRequestDTO request = new ToggleRequestDTO();

        controller.toggleGateway(request);

        verify(gatewayAccessService).toggleGateway(request);
    }

    @Test
    void shouldToggleCertificate() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);
        ToggleRequestDTO request = new ToggleRequestDTO();

        controller.toggleCertificate(request);

        verify(gatewayAccessService).toggleCertificate(request);
    }

    @Test
    void shouldTogglePermission() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);
        ToggleRequestDTO request = new ToggleRequestDTO();

        controller.togglePermission(request);

        verify(gatewayAccessService).togglePermission(request);
    }

    @Test
    void shouldToggleReleaseRoute() {
        GatewayAccessController controller = new GatewayAccessController(gatewayAccessService);
        ToggleRequestDTO request = new ToggleRequestDTO();

        controller.toggleReleaseRoute(request);

        verify(gatewayAccessService).toggleReleaseRoute(request);
    }
}
