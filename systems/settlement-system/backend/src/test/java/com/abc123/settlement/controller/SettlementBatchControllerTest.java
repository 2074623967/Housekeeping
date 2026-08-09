package com.abc123.settlement.controller;

import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.CreateSettlementBatchRequestDTO;
import com.abc123.settlement.service.SettlementBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 结算批次控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementBatchControllerTest {

    @Mock
    private SettlementBatchService settlementBatchService;

    @Test
    void shouldListSettlementBatches() {
        SettlementBatchController controller = new SettlementBatchController(settlementBatchService);

        controller.list("2026-08-03", "待出款", 1, 20);

        verify(settlementBatchService).list("2026-08-03", "待出款", 1, 20);
    }

    @Test
    void shouldCreateSettlementBatch() {
        SettlementBatchController controller = new SettlementBatchController(settlementBatchService);
        CreateSettlementBatchRequestDTO request = new CreateSettlementBatchRequestDTO();

        controller.create(request);

        verify(settlementBatchService).create(request);
    }

    @Test
    void shouldReturnSettlementBatchDetail() {
        SettlementBatchController controller = new SettlementBatchController(settlementBatchService);

        controller.detail("SET10001");

        verify(settlementBatchService).detail("SET10001");
    }
}
