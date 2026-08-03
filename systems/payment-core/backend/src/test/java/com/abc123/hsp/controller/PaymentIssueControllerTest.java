package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertAcknowledgeRequestDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.service.PaymentIssueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付交易异常中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueControllerTest {

    @Mock
    private PaymentIssueService paymentIssueService;

    @Test
    void shouldListPaymentIssues() {
        PaymentIssueController controller = new PaymentIssueController(paymentIssueService);

        controller.list("PAY-001", "ORD-001", "待回调未收口", "P1", "wx_jsapi", "微信", 1, 20);

        verify(paymentIssueService).list(any(PaymentIssueQueryDTO.class));
    }

    @Test
    void shouldReturnResponsibilitySummary() {
        PaymentIssueController controller = new PaymentIssueController(paymentIssueService);

        controller.responsibilitySummary("PAY-001", "ORD-001", "待回调未收口", "P1", "wx_jsapi", "微信");

        verify(paymentIssueService).responsibilitySummary(any(PaymentIssueQueryDTO.class));
    }

    @Test
    void shouldListAlertLogs() {
        PaymentIssueController controller = new PaymentIssueController(paymentIssueService);

        controller.listAlertLogs("ALERT-001", "ISSUE-001", "PAY-001", "IM", "已派发", "待确认", "ACCEPTED", 1, 20);

        verify(paymentIssueService).listAlertLogs(any(PaymentIssueAlertLogQueryDTO.class));
    }

    @Test
    void shouldAcknowledgeAlert() {
        PaymentIssueController controller = new PaymentIssueController(paymentIssueService);
        PaymentIssueAlertAcknowledgeRequestDTO request = new PaymentIssueAlertAcknowledgeRequestDTO();
        request.setOperator("支付运营");

        controller.acknowledgeAlert("ALERT-001", request);

        verify(paymentIssueService).acknowledgeAlert("ALERT-001", request);
    }

    @Test
    void shouldBatchActionIssues() {
        PaymentIssueController controller = new PaymentIssueController(paymentIssueService);
        PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();

        controller.batchAction(request);

        verify(paymentIssueService).batchAction(request);
    }
}
