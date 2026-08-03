package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.CreateAdjustmentRequestDTO;
import com.abc123.accounting.service.AdjustmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 调账控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class AdjustmentControllerTest {

    @Mock
    private AdjustmentService adjustmentService;

    @Test
    void shouldListAdjustments() {
        AdjustmentController controller = new AdjustmentController(adjustmentService);

        controller.list("ACT10001", "待审核", 1, 20);

        verify(adjustmentService).list("ACT10001", "待审核", 1, 20);
    }

    @Test
    void shouldCreateAdjustment() {
        AdjustmentController controller = new AdjustmentController(adjustmentService);
        CreateAdjustmentRequestDTO request = new CreateAdjustmentRequestDTO();

        controller.create(request);

        verify(adjustmentService).create(request);
    }

    @Test
    void shouldApproveAdjustment() {
        AdjustmentController controller = new AdjustmentController(adjustmentService);
        ApproveAdjustmentRequestDTO request = new ApproveAdjustmentRequestDTO();

        controller.approve("ADJ40001", request);

        verify(adjustmentService).approve("ADJ40001", request);
    }
}
