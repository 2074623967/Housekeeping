package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.dto.CreateClearingBatchRequestDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;
import com.abc123.clearing.service.ClearingBatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 清分批次控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingBatchControllerTest {

    @Mock
    private ClearingBatchService clearingBatchService;

    @Test
    void shouldListClearingBatches() {
        ClearingBatchController controller = new ClearingBatchController(clearingBatchService);

        controller.list("2026-08-03", "待执行", 1, 20);

        verify(clearingBatchService).list("2026-08-03", "待执行", 1, 20);
    }

    @Test
    void shouldCreateClearingBatch() {
        ClearingBatchController controller = new ClearingBatchController(clearingBatchService);
        CreateClearingBatchRequestDTO request = new CreateClearingBatchRequestDTO();

        controller.create(request);

        verify(clearingBatchService).create(request);
    }

    @Test
    void shouldReturnClearingBatchDetail() {
        ClearingBatchController controller = new ClearingBatchController(clearingBatchService);

        controller.detail("CLB10001");

        verify(clearingBatchService).detail("CLB10001");
    }

    @Test
    void shouldRerunClearingBatch() {
        ClearingBatchController controller = new ClearingBatchController(clearingBatchService);
        RerunClearingBatchRequestDTO request = new RerunClearingBatchRequestDTO();

        controller.rerun("CLB10001", request);

        verify(clearingBatchService).rerun("CLB10001", request);
    }
}
