package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 清分规则服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClearingRuleServiceImplTest {

    @Autowired
    private ClearingRuleServiceImpl clearingRuleService;

    @Test
    void shouldToggleRuleStatus() {
        ClearingRuleDTO disabledRule = clearingRuleService.disable("CLR30001");
        ClearingRuleDTO enabledRule = clearingRuleService.enable("CLR30001");
        PageResultDTO<ClearingRuleDTO> result = clearingRuleService.list("", "", 1, 20);

        assertEquals("停用", disabledRule.getRuleStatus());
        assertEquals("启用", enabledRule.getRuleStatus());
        assertEquals(1, result.getTotal());
    }
}
