package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.riskcontrol.dao.impl.RiskControlDaoImpl;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.mapper.RiskControlMapper;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 风控 DAO 空结果边界测试。
 */
class RiskControlDaoEmptyListTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyLists() {
        RiskControlMapper mapper = mock(RiskControlMapper.class);
        when(mapper.findPolicies()).thenReturn(null);
        when(mapper.findLimitRules()).thenReturn(null);
        when(mapper.findBlocklists()).thenReturn(null);
        when(mapper.findInterceptEvents()).thenReturn(null);
        when(mapper.findReviewOrders()).thenReturn(null);
        when(mapper.findMonitorRules()).thenReturn(null);
        when(mapper.findEnabledBlocklists()).thenReturn(null);
        when(mapper.findEnabledLimitRulesByScene("PAY_CONSUME")).thenReturn(null);
        when(mapper.findEnabledPoliciesForDecision()).thenReturn(null);

        RiskControlDaoImpl dao = new RiskControlDaoImpl(mapper);

        assertTrue(dao.findPolicies().isEmpty());
        assertTrue(dao.findLimitRules().isEmpty());
        assertTrue(dao.findBlocklists().isEmpty());
        assertTrue(dao.findInterceptEvents().isEmpty());
        assertTrue(dao.findReviewOrders().isEmpty());
        assertTrue(dao.findMonitorRules().isEmpty());
        assertTrue(dao.findEnabledBlocklists().isEmpty());
        assertTrue(dao.findEnabledLimitRulesByScene("PAY_CONSUME").isEmpty());
        assertTrue(dao.findEnabledPoliciesForDecision().isEmpty());
    }

    @Test
    void shouldFilterNullRowsWithoutDroppingValidRows() {
        RiskControlMapper mapper = mock(RiskControlMapper.class);
        RiskPolicyDTO valid = mock(RiskPolicyDTO.class);
        when(mapper.findPolicies()).thenReturn(Arrays.asList(null, valid, null));

        RiskControlDaoImpl dao = new RiskControlDaoImpl(mapper);

        assertSame(valid, dao.findPolicies().get(0));
        assertTrue(dao.findPolicies().size() == 1);
    }
}
