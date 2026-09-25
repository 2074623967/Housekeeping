package com.abc123.opsconfig;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.opsconfig.dao.impl.OpsConfigDaoImpl;
import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.mapper.OpsConfigMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 运营配置 DAO 空结果边界测试。
 */
class OpsConfigDaoEmptyListTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyLists() {
        OpsConfigMapper mapper = mock(OpsConfigMapper.class);
        when(mapper.findAgreementTemplates()).thenReturn(null);
        when(mapper.findBusinessLines()).thenReturn(null);
        when(mapper.findPaymentTypes()).thenReturn(null);
        when(mapper.findCashierTemplates()).thenReturn(null);
        when(mapper.findChannelProfiles()).thenReturn(null);
        when(mapper.findRoutingRules()).thenReturn(null);
        when(mapper.findSystemControls()).thenReturn(null);
        when(mapper.findEnabledSystemControls()).thenReturn(null);
        when(mapper.findConfigChanges()).thenReturn(null);
        when(mapper.findRefreshEvents()).thenReturn(null);

        OpsConfigDaoImpl dao = new OpsConfigDaoImpl(mapper);

        assertTrue(dao.findAgreementTemplates().isEmpty());
        assertTrue(dao.findBusinessLines().isEmpty());
        assertTrue(dao.findPaymentTypes().isEmpty());
        assertTrue(dao.findCashierTemplates().isEmpty());
        assertTrue(dao.findChannelProfiles().isEmpty());
        assertTrue(dao.findRoutingRules().isEmpty());
        assertTrue(dao.findSystemControls().isEmpty());
        assertTrue(dao.findEnabledSystemControls().isEmpty());
        assertTrue(dao.findConfigChanges().isEmpty());
        assertTrue(dao.findRefreshEvents().isEmpty());
    }

    @Test
    void shouldFilterNullRowsWithoutDroppingValidRows() {
        OpsConfigMapper mapper = mock(OpsConfigMapper.class);
        AgreementTemplateDTO valid = mock(AgreementTemplateDTO.class);
        when(mapper.findAgreementTemplates()).thenReturn(Arrays.asList(null, valid, null));

        OpsConfigDaoImpl dao = new OpsConfigDaoImpl(mapper);

        assertSame(valid, dao.findAgreementTemplates().get(0));
        assertTrue(dao.findAgreementTemplates().size() == 1);
    }
}
