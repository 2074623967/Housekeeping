package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.dto.CreateClearingRuleRequestDTO;
import com.abc123.clearing.service.ClearingRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 清分规则控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingRuleControllerTest {

    @Mock
    private ClearingRuleService clearingRuleService;

    @Test
    void shouldListClearingRules() {
        ClearingRuleController controller = new ClearingRuleController(clearingRuleService);

        controller.list("SERVICE", "启用", 1, 20);

        verify(clearingRuleService).list("SERVICE", "启用", 1, 20);
    }

    @Test
    void shouldCreateClearingRule() {
        ClearingRuleController controller = new ClearingRuleController(clearingRuleService);
        CreateClearingRuleRequestDTO request = new CreateClearingRuleRequestDTO();

        controller.create(request);

        verify(clearingRuleService).create(request);
    }

    @Test
    void shouldEnableClearingRule() {
        ClearingRuleController controller = new ClearingRuleController(clearingRuleService);

        controller.enable("RULE30001");

        verify(clearingRuleService).enable("RULE30001");
    }

    @Test
    void shouldDisableClearingRule() {
        ClearingRuleController controller = new ClearingRuleController(clearingRuleService);

        controller.disable("RULE30001");

        verify(clearingRuleService).disable("RULE30001");
    }
}
