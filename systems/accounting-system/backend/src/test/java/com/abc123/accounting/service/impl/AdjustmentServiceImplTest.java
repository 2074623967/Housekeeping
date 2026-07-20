package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import org.junit.jupiter.api.Test;

/**
 * 调账服务测试。
 */
class AdjustmentServiceImplTest {

    @Test
    void shouldApplyAdjustmentWhenApproved() {
        AccountingMemoryStore store = new AccountingMemoryStore();
        store.initDemoData();
        AccountingMapper mapper = new AccountingMapper();
        AdjustmentServiceImpl adjustmentService = new AdjustmentServiceImpl(store, mapper);
        BalanceServiceImpl balanceService = new BalanceServiceImpl(store, mapper);

        ApproveAdjustmentRequestDTO request = new ApproveAdjustmentRequestDTO();
        request.setApprovedBy("财务主管");

        AdjustmentItemDTO result = adjustmentService.approve("ADJ40001", request);
        BalanceSnapshotDTO balance = balanceService.detail("ACT10002");

        assertEquals("已生效", result.getAdjustStatus());
        assertEquals("¥128.00", balance.getAvailableAmount());
    }
}
