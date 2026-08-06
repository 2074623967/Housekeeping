package com.abc123.opsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.opsconfig.common.BusinessException;
import com.abc123.opsconfig.dto.OpsConfigEffectiveSnapshotDTO;
import com.abc123.opsconfig.dto.OpsConfigSnapshotQueryDTO;
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

    @Test
    void shouldLoadEnabledEffectiveSnapshot() {
        OpsConfigSnapshotQueryDTO query = new OpsConfigSnapshotQueryDTO();
        query.setBusinessCode("HOME_CLEAN");
        query.setPayType("PAY_CONSUME");
        query.setTerminalType("APP");

        OpsConfigEffectiveSnapshotDTO snapshot = service.effectiveSnapshot(query);

        assertEquals("微信支付", snapshot.getDefaultPayMethod());
        assertEquals("CHANNEL_WX_H5", snapshot.getPrimaryChannelProfileCode());
        assertEquals("CHANNEL_ALI_APP", snapshot.getBackupChannelProfileCode());
        assertEquals(2, snapshot.getEnabledSystemControls().size());
    }

    @Test
    void shouldRejectDisabledBusinessLineWhenLoadingSnapshot() {
        OpsConfigSnapshotQueryDTO query = new OpsConfigSnapshotQueryDTO();
        query.setBusinessCode("HOME_DEPOSIT");
        query.setPayType("PAY_DEPOSIT");
        query.setTerminalType("APP");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.effectiveSnapshot(query));
        assertEquals("业务线未启用或不存在", exception.getMessage());
    }

    @Test
    void shouldRejectMissingEnabledCashierTemplateWhenLoadingSnapshot() {
        OpsConfigSnapshotQueryDTO query = new OpsConfigSnapshotQueryDTO();
        query.setBusinessCode("HOME_CLEAN");
        query.setPayType("PAY_CONSUME");
        query.setTerminalType("PC");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.effectiveSnapshot(query));
        assertEquals("终端未配置启用中的收银台模板", exception.getMessage());
    }

    @Test
    void shouldRejectMissingEnabledRoutingRuleWhenLoadingSnapshot() {
        OpsConfigSnapshotQueryDTO query = new OpsConfigSnapshotQueryDTO();
        query.setBusinessCode("HOME_NANNY");
        query.setPayType("PAY_DEPOSIT");
        query.setTerminalType("APP");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.effectiveSnapshot(query));
        assertEquals("业务线与支付类型未配置启用中的路由规则", exception.getMessage());
    }
}
