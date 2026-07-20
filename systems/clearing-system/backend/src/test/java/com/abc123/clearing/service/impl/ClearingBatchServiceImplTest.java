package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;
import org.junit.jupiter.api.Test;

/**
 * 清分批次服务测试。
 */
class ClearingBatchServiceImplTest {

    @Test
    void shouldCreateNextVersionBatchWhenRerun() {
        ClearingMemoryStore store = new ClearingMemoryStore();
        store.initDemoData();
        ClearingMapper mapper = new ClearingMapper();
        ClearingBatchServiceImpl batchService = new ClearingBatchServiceImpl(store, mapper);

        RerunClearingBatchRequestDTO request = new RerunClearingBatchRequestDTO();
        request.setOperatorName("清分运营");
        request.setReason("补偿重跑");

        ClearingBatchDTO result = batchService.rerun("CLB10001", request);

        assertEquals("V2", result.getVersionNo());
        assertEquals("已完成", result.getBatchStatus());
    }
}
