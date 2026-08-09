package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.service.PaymentConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付配置中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfigControllerTest {

    @Mock
    private PaymentConfigService paymentConfigService;

    @Test
    void shouldReturnOverview() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        overview.setChannels(java.util.Collections.emptyList());
        when(paymentConfigService.overview()).thenReturn(overview);

        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        assertEquals(0, controller.overview().getData().getChannels().size());
        verify(paymentConfigService).overview();
    }

    @Test
    void shouldExportGovernanceSnapshot() {
        when(paymentConfigService.exportGovernanceSnapshotCsv("CHANNELS")).thenReturn("config-csv");

        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);
        String body = new String(controller.export("CHANNELS").getBody(), java.nio.charset.StandardCharsets.UTF_8);

        verify(paymentConfigService).exportGovernanceSnapshotCsv("CHANNELS");
        assertTrue(body.contains("config-csv"));
    }

    @Test
    void shouldCreateProtocol() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.createProtocol(any(PaymentProtocolUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentProtocolUpsertRequestDTO request = new PaymentProtocolUpsertRequestDTO();
        request.setProtocolCode("PROTO_HOME_SIGN");

        assertEquals(overview, controller.createProtocol(request).getData());
        verify(paymentConfigService).createProtocol(request);
    }

    @Test
    void shouldUpdateProtocol() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.updateProtocol(any(String.class), any(PaymentProtocolUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentProtocolUpsertRequestDTO request = new PaymentProtocolUpsertRequestDTO();
        request.setProtocolName("家政代扣协议");

        assertEquals(overview, controller.updateProtocol("PROTO_HOME_SIGN", request).getData());
        verify(paymentConfigService).updateProtocol("PROTO_HOME_SIGN", request);
    }

    @Test
    void shouldCreateIssueDutyRoster() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.createIssueDutyRoster(any(PaymentIssueDutyRosterUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode("ROSTER_CALLBACK_P1");

        assertEquals(overview, controller.createIssueDutyRoster(request).getData());
        verify(paymentConfigService).createIssueDutyRoster(request);
    }

    @Test
    void shouldUpdateIssueDutyRoster() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.updateIssueDutyRoster(any(String.class), any(PaymentIssueDutyRosterUpsertRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setReceiver("支付后端值班");

        assertEquals(overview, controller.updateIssueDutyRoster("ROSTER_CALLBACK_P1", request).getData());
        verify(paymentConfigService).updateIssueDutyRoster("ROSTER_CALLBACK_P1", request);
    }

    @Test
    void shouldToggleGovernanceConfigs() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.toggleChannel(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleRouteRule(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleProtocol(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleReturnCodeMapping(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleGateway(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleControlPolicy(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleAlertProvider(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        when(paymentConfigService.toggleIssueDutyRoster(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("WX_H5");
        request.setEnabled(Boolean.TRUE);

        assertEquals(overview, controller.toggleChannel(request).getData());
        assertEquals(overview, controller.toggleRouteRule(request).getData());
        assertEquals(overview, controller.toggleProtocol(request).getData());
        assertEquals(overview, controller.toggleReturnCodeMapping(request).getData());
        assertEquals(overview, controller.toggleGateway(request).getData());
        assertEquals(overview, controller.toggleControlPolicy(request).getData());
        assertEquals(overview, controller.toggleAlertProvider(request).getData());
        assertEquals(overview, controller.toggleIssueDutyRoster(request).getData());
        verify(paymentConfigService).toggleChannel(request);
        verify(paymentConfigService).toggleRouteRule(request);
        verify(paymentConfigService).toggleProtocol(request);
        verify(paymentConfigService).toggleReturnCodeMapping(request);
        verify(paymentConfigService).toggleGateway(request);
        verify(paymentConfigService).toggleControlPolicy(request);
        verify(paymentConfigService).toggleAlertProvider(request);
        verify(paymentConfigService).toggleIssueDutyRoster(request);
    }

    @Test
    void shouldRunControlPolicySelfCheck() {
        PaymentConfigOverviewDTO overview = new PaymentConfigOverviewDTO();
        when(paymentConfigService.runControlPolicySelfCheck(any(PaymentConfigToggleRequestDTO.class))).thenReturn(overview);
        PaymentConfigController controller = new PaymentConfigController(paymentConfigService);

        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("APP_HOME_SERVICE");

        assertEquals(overview, controller.runControlPolicySelfCheck(request).getData());
        verify(paymentConfigService).runControlPolicySelfCheck(request);
    }
}
