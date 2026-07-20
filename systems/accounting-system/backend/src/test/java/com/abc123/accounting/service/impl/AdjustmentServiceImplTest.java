package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 调账服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AdjustmentServiceImplTest {

    @Autowired
    private AdjustmentServiceImpl adjustmentService;

    @Autowired
    private BalanceServiceImpl balanceService;

    @Test
    void shouldApplyAdjustmentWhenApproved() {
        ApproveAdjustmentRequestDTO request = new ApproveAdjustmentRequestDTO();
        request.setApprovedBy("财务主管");

        AdjustmentItemDTO result = adjustmentService.approve("ADJ40001", request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10002");

        assertEquals("已生效", result.getAdjustStatus());
        assertEquals("¥128.00", balance.getAvailableAmount());
    }
}
