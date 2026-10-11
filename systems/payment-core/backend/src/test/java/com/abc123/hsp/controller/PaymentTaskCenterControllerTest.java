package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.BlockedExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskOperatorRequestDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付任务中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentTaskCenterControllerTest {

    @Mock
    private PaymentTaskCenterService paymentTaskCenterService;

    @Test
    void shouldListBlockedExpiredPaymentsWithoutAction() {
        PageResultDTO<BlockedExpiredPaymentDTO> page =
                new PageResultDTO<>(Collections.emptyList(), 0, 2, 20);
        when(paymentTaskCenterService.listBlockedExpiredPayments(2, 20)).thenReturn(page);

        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        assertEquals(page, controller.listBlockedExpiredPayments(2, 20).getData());
        verify(paymentTaskCenterService).listBlockedExpiredPayments(2, 20);
    }

    @Test
    void shouldReturnTaskCenterOverview() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        PaymentTaskCenterOverviewDTO overviewDTO = new PaymentTaskCenterOverviewDTO();
        overviewDTO.setExpiredPaymentCount(3);
        overviewDTO.setDueIssueAlertReceiptCount(7);
        when(paymentTaskCenterService.overview()).thenReturn(overviewDTO);

        PaymentTaskCenterOverviewDTO actual = controller.overview().getData();
        assertEquals(3, actual.getExpiredPaymentCount());
        assertEquals(7, actual.getDueIssueAlertReceiptCount());
        verify(paymentTaskCenterService).overview();
    }

    @Test
    void shouldListTaskRuns() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        PaymentTaskRunLogItemDTO itemDTO = new PaymentTaskRunLogItemDTO();
        itemDTO.setTaskCode("PAYMENT_EVENT_RETRY");
        PageResultDTO<PaymentTaskRunLogItemDTO> resultDTO =
                new PageResultDTO<PaymentTaskRunLogItemDTO>(Collections.singletonList(itemDTO), 1L, 2, 20);
        when(paymentTaskCenterService.listTaskRuns(any(PaymentTaskRunLogQueryDTO.class))).thenReturn(resultDTO);

        controller.listTaskRuns("PAYMENT_EVENT_RETRY", "2026-09-11", "MANUAL", "SUCCESS", "P2", 2, 20);

        ArgumentCaptor<PaymentTaskRunLogQueryDTO> queryCaptor =
                ArgumentCaptor.forClass(PaymentTaskRunLogQueryDTO.class);
        verify(paymentTaskCenterService).listTaskRuns(queryCaptor.capture());
        PaymentTaskRunLogQueryDTO queryDTO = queryCaptor.getValue();

        assertNotNull(queryDTO);
        assertEquals("PAYMENT_EVENT_RETRY", queryDTO.getTaskCode());
        assertEquals("2026-09-11", queryDTO.getBizDate());
        assertEquals("MANUAL", queryDTO.getRunMode());
        assertEquals("SUCCESS", queryDTO.getTaskStatus());
        assertEquals("P2", queryDTO.getSeverityLevel());
        assertEquals(2, queryDTO.getPageNo());
        assertEquals(20, queryDTO.getPageSize());
    }

    @Test
    void shouldReturnTaskRunDetail() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);

        controller.taskRunDetail("TASK-LOG-001");

        verify(paymentTaskCenterService).taskRunDetail("TASK-LOG-001");
    }

    @Test
    void shouldRunManualPaymentTasks() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        PaymentTaskActionResultDTO resultDTO = new PaymentTaskActionResultDTO();
        resultDTO.setTaskCode("PAYMENT_EXPIRE_CLOSE");
        resultDTO.setProcessedCount(4);
        when(paymentTaskCenterService.runCloseExpiredPayments("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runPendingPaymentQueries("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runPendingRefundQueries("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runGenerateQueryMonitorAlerts("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runRepublishFailedEvents("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runRetryFailedRefunds("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runEscalateOverdueIssues("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runDispatchIssueAlerts("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runReconcileIssueAlertReceipts("支付运营")).thenReturn(resultDTO);
        when(paymentTaskCenterService.runReconcileIssueAlertReceipt("PIA-001", "支付运营"))
                .thenReturn(resultDTO);
        when(paymentTaskCenterService.runControlPolicySelfChecks("支付运营")).thenReturn(resultDTO);
        PaymentTaskOperatorRequestDTO request = new PaymentTaskOperatorRequestDTO();
        request.setOperator("支付运营");

        assertEquals(4, controller.runCloseExpiredPayments(request).getData().getProcessedCount());
        assertEquals(4, controller.runPendingPaymentQueries(request).getData().getProcessedCount());
        assertEquals(4, controller.runPendingRefundQueries(request).getData().getProcessedCount());
        assertEquals(4, controller.runGenerateQueryMonitorAlerts(request).getData().getProcessedCount());
        assertEquals(4, controller.runRepublishFailedEvents(request).getData().getProcessedCount());
        assertEquals(4, controller.runRetryFailedRefunds(request).getData().getProcessedCount());
        assertEquals(4, controller.runEscalateOverdueIssues(request).getData().getProcessedCount());
        assertEquals(4, controller.runDispatchIssueAlerts(request).getData().getProcessedCount());
        assertEquals(4, controller.runReconcileIssueAlertReceipts(request).getData().getProcessedCount());
        assertEquals(4, controller.runReconcileIssueAlertReceipt(
                "PIA-001", request).getData().getProcessedCount());
        assertEquals(4, controller.runControlPolicySelfChecks(request).getData().getProcessedCount());

        verify(paymentTaskCenterService).runCloseExpiredPayments("支付运营");
        verify(paymentTaskCenterService).runPendingPaymentQueries("支付运营");
        verify(paymentTaskCenterService).runPendingRefundQueries("支付运营");
        verify(paymentTaskCenterService).runGenerateQueryMonitorAlerts("支付运营");
        verify(paymentTaskCenterService).runRepublishFailedEvents("支付运营");
        verify(paymentTaskCenterService).runRetryFailedRefunds("支付运营");
        verify(paymentTaskCenterService).runEscalateOverdueIssues("支付运营");
        verify(paymentTaskCenterService).runDispatchIssueAlerts("支付运营");
        verify(paymentTaskCenterService).runReconcileIssueAlertReceipts("支付运营");
        verify(paymentTaskCenterService).runReconcileIssueAlertReceipt("PIA-001", "支付运营");
        verify(paymentTaskCenterService).runControlPolicySelfChecks("支付运营");
    }

    @Test
    void shouldExportTaskRuns() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        when(paymentTaskCenterService.exportTaskRunsCsv(any(PaymentTaskRunLogQueryDTO.class))).thenReturn("task-run-csv");

        String body = new String(controller.exportTaskRuns(
                "PAYMENT_EVENT_RETRY", "2026-09-11", "MANUAL", "SUCCESS", "P2")
                .getBody(), java.nio.charset.StandardCharsets.UTF_8);

        ArgumentCaptor<PaymentTaskRunLogQueryDTO> queryCaptor =
                ArgumentCaptor.forClass(PaymentTaskRunLogQueryDTO.class);
        verify(paymentTaskCenterService).exportTaskRunsCsv(queryCaptor.capture());
        assertEquals("PAYMENT_EVENT_RETRY", queryCaptor.getValue().getTaskCode());
        assertEquals("2026-09-11", queryCaptor.getValue().getBizDate());
        assertEquals("MANUAL", queryCaptor.getValue().getRunMode());
        assertEquals("SUCCESS", queryCaptor.getValue().getTaskStatus());
        assertEquals("P2", queryCaptor.getValue().getSeverityLevel());
        assertTrue(body.contains("task-run-csv"));
    }
}
