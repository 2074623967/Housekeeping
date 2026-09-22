package com.abc123.settlement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
import java.nio.charset.StandardCharsets;
import com.abc123.settlement.service.PayoutInspectionService;
import com.abc123.settlement.service.PayoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 出款控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PayoutControllerTest {

    @Mock
    private PayoutService payoutService;

    @Mock
    private PayoutInspectionService payoutInspectionService;

    @Test
    void shouldListPayoutBatches() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);

        controller.list("PBT50001", "待出款", 1, 20);

        verify(payoutService).list("PBT50001", "待出款", 1, 20);
    }

    @Test
    void shouldCreatePayoutBatch() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);
        CreatePayoutBatchRequestDTO request = new CreatePayoutBatchRequestDTO();

        controller.create(request);

        verify(payoutService).create(request);
    }

    @Test
    void shouldExecutePayoutBatch() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);
        ExecutePayoutBatchRequestDTO request = new ExecutePayoutBatchRequestDTO();

        controller.execute("PBT50001", request);

        verify(payoutService).execute("PBT50001", request);
    }

    @Test
    void shouldRetryPayoutBatch() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);
        RetryPayoutBatchRequestDTO request = new RetryPayoutBatchRequestDTO();

        controller.retry("PBT50001", request);

        verify(payoutService).retry("PBT50001", request);
    }

    @Test
    void shouldListPayoutRecords() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);

        controller.records("PBT50001", "出款失败", 1, 20);

        verify(payoutService).records("PBT50001", "出款失败", 1, 20);
    }

    @Test
    void shouldExportPayoutBatches() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);
        when(payoutService.exportBatches("SET10001", "待出款")).thenReturn("csv".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportBatches("SET10001", "待出款").getBody();

        verify(payoutService).exportBatches("SET10001", "待出款");
        assertEquals("csv", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void shouldExportPayoutRecords() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);
        when(payoutService.exportRecords("PBT50001", "已失败")).thenReturn("record".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportRecords("PBT50001", "已失败").getBody();

        verify(payoutService).exportRecords("PBT50001", "已失败");
        assertEquals("record", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void shouldInspectPayoutBatches() {
        PayoutController controller = new PayoutController(payoutService, payoutInspectionService);

        controller.inspect();

        verify(payoutInspectionService).inspect();
    }
}
