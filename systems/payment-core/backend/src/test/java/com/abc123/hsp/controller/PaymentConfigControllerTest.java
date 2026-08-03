package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
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
}
