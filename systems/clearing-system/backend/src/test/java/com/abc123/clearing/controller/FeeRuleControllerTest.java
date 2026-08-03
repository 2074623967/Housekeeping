package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.dto.CreateFeeRuleRequestDTO;
import com.abc123.clearing.service.FeeRuleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 费用规则控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class FeeRuleControllerTest {

    @Mock
    private FeeRuleService feeRuleService;

    @Test
    void shouldListFeeRules() {
        FeeRuleController controller = new FeeRuleController(feeRuleService);

        controller.list("PLATFORM", "启用", 1, 20);

        verify(feeRuleService).list("PLATFORM", "启用", 1, 20);
    }

    @Test
    void shouldCreateFeeRule() {
        FeeRuleController controller = new FeeRuleController(feeRuleService);
        CreateFeeRuleRequestDTO request = new CreateFeeRuleRequestDTO();

        controller.create(request);

        verify(feeRuleService).create(request);
    }
}
