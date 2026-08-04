package com.abc123.opsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.opsconfig.common.BusinessException;
import com.abc123.opsconfig.dto.ToggleRequestDTO;
import com.abc123.opsconfig.service.OpsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证运营配置核心链路。
 */
@SpringBootTest
class OpsConfigIntegrationTest {

    @Autowired
    private OpsConfigService service;

    @Test
    void shouldLoadOpsConfigOverviewAndLists() {
        assertEquals(4, service.summary().getMetrics().size());
        assertEquals(3, service.agreementTemplates().getRecords().size());
        assertEquals(3, service.routingRules().getRecords().size());
    }

    @Test
    void shouldToggleBusinessConfigStatus() {
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setConfigCode("ROUTE_HOME_DEPOSIT");
        request.setEnabled(true);
        service.toggleRoutingRule(request);
        assertEquals("ENABLED", service.routingRules().getRecords().get(2).getStatus());
    }

    @Test
    void shouldRejectEmptyToggleCode() {
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setEnabled(true);
        assertThrows(BusinessException.class, () -> service.toggleSystemControl(request));
    }
}
