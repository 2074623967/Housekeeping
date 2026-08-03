package com.abc123.settlement.controller;

import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
import com.abc123.settlement.service.PayoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 出款控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PayoutControllerTest {

    @Mock
    private PayoutService payoutService;

    @Test
    void shouldListPayoutBatches() {
        PayoutController controller = new PayoutController(payoutService);

        controller.list("PBT50001", "待出款", 1, 20);

        verify(payoutService).list("PBT50001", "待出款", 1, 20);
    }

    @Test
    void shouldCreatePayoutBatch() {
        PayoutController controller = new PayoutController(payoutService);
        CreatePayoutBatchRequestDTO request = new CreatePayoutBatchRequestDTO();

        controller.create(request);

        verify(payoutService).create(request);
    }

    @Test
    void shouldExecutePayoutBatch() {
        PayoutController controller = new PayoutController(payoutService);
        ExecutePayoutBatchRequestDTO request = new ExecutePayoutBatchRequestDTO();

        controller.execute("PBT50001", request);

        verify(payoutService).execute("PBT50001", request);
    }

    @Test
    void shouldRetryPayoutBatch() {
        PayoutController controller = new PayoutController(payoutService);
        RetryPayoutBatchRequestDTO request = new RetryPayoutBatchRequestDTO();

        controller.retry("PBT50001", request);

        verify(payoutService).retry("PBT50001", request);
    }

    @Test
    void shouldListPayoutRecords() {
        PayoutController controller = new PayoutController(payoutService);

        controller.records("PBT50001", "出款失败", 1, 20);

        verify(payoutService).records("PBT50001", "出款失败", 1, 20);
    }
}
