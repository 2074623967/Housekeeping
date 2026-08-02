package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
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
    void shouldReturnTaskCenterOverview() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        PaymentTaskCenterOverviewDTO overviewDTO = new PaymentTaskCenterOverviewDTO();
        overviewDTO.setExpiredPaymentCount(3);
        when(paymentTaskCenterService.overview()).thenReturn(overviewDTO);

        assertEquals(3, controller.overview().getData().getExpiredPaymentCount());
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

        controller.listTaskRuns("PAYMENT_EVENT_RETRY", "MANUAL", "SUCCESS", "P2", 2, 20);

        ArgumentCaptor<PaymentTaskRunLogQueryDTO> queryCaptor =
                ArgumentCaptor.forClass(PaymentTaskRunLogQueryDTO.class);
        verify(paymentTaskCenterService).listTaskRuns(queryCaptor.capture());
        PaymentTaskRunLogQueryDTO queryDTO = queryCaptor.getValue();

        assertNotNull(queryDTO);
        assertEquals("PAYMENT_EVENT_RETRY", queryDTO.getTaskCode());
        assertEquals("MANUAL", queryDTO.getRunMode());
        assertEquals("SUCCESS", queryDTO.getTaskStatus());
        assertEquals("P2", queryDTO.getSeverityLevel());
        assertEquals(2, queryDTO.getPageNo());
        assertEquals(20, queryDTO.getPageSize());
    }

    @Test
    void shouldRunManualPaymentTasks() {
        PaymentTaskCenterController controller = new PaymentTaskCenterController(paymentTaskCenterService);
        PaymentTaskActionResultDTO resultDTO = new PaymentTaskActionResultDTO();
        resultDTO.setTaskCode("PAYMENT_EXPIRE_CLOSE");
        resultDTO.setProcessedCount(4);
        when(paymentTaskCenterService.runCloseExpiredPayments()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runRepublishFailedEvents()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runRetryFailedRefunds()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runEscalateOverdueIssues()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runDispatchIssueAlerts()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runReconcileIssueAlertReceipts()).thenReturn(resultDTO);
        when(paymentTaskCenterService.runControlPolicySelfChecks()).thenReturn(resultDTO);

        assertEquals(4, controller.runCloseExpiredPayments().getData().getProcessedCount());
        assertEquals(4, controller.runRepublishFailedEvents().getData().getProcessedCount());
        assertEquals(4, controller.runRetryFailedRefunds().getData().getProcessedCount());
        assertEquals(4, controller.runEscalateOverdueIssues().getData().getProcessedCount());
        assertEquals(4, controller.runDispatchIssueAlerts().getData().getProcessedCount());
        assertEquals(4, controller.runReconcileIssueAlertReceipts().getData().getProcessedCount());
        assertEquals(4, controller.runControlPolicySelfChecks().getData().getProcessedCount());

        verify(paymentTaskCenterService).runCloseExpiredPayments();
        verify(paymentTaskCenterService).runRepublishFailedEvents();
        verify(paymentTaskCenterService).runRetryFailedRefunds();
        verify(paymentTaskCenterService).runEscalateOverdueIssues();
        verify(paymentTaskCenterService).runDispatchIssueAlerts();
        verify(paymentTaskCenterService).runReconcileIssueAlertReceipts();
        verify(paymentTaskCenterService).runControlPolicySelfChecks();
    }
}
