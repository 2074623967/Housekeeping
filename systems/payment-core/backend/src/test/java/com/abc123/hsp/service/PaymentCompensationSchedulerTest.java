package com.abc123.hsp.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付失败补偿调度器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompensationSchedulerTest {

    @Mock
    private PaymentTaskCenterService paymentTaskCenterService;

    @Test
    void shouldQueryPendingPaymentsWhenCompensationEnabled() {
        new PaymentCompensationScheduler(paymentTaskCenterService, true).queryPendingPayments();

        verify(paymentTaskCenterService).runAutoPendingPaymentQueries();
    }

    @Test
    void shouldSkipPendingPaymentQueryWhenCompensationDisabled() {
        new PaymentCompensationScheduler(paymentTaskCenterService, false).queryPendingPayments();

        verify(paymentTaskCenterService, never()).runAutoPendingPaymentQueries();
    }

    @Test
    void shouldRequireGatewayAndCompensationSwitchesForPendingQueries() {
        new PaymentCompensationScheduler(paymentTaskCenterService, true, false).queryPendingPayments();

        verify(paymentTaskCenterService, never()).runAutoPendingPaymentQueries();
    }

    @Test
    void shouldRequireGatewayAndCompensationSwitchesForPendingRefundQueries() {
        new PaymentCompensationScheduler(paymentTaskCenterService, false, false, true, true, false)
                .queryPendingRefunds();
        verify(paymentTaskCenterService).runAutoPendingRefundQueries();

        org.mockito.Mockito.reset(paymentTaskCenterService);
        new PaymentCompensationScheduler(paymentTaskCenterService, false, false, true, false, false)
                .queryPendingRefunds();
        verify(paymentTaskCenterService, never()).runAutoPendingRefundQueries();
    }

    @Test
    void shouldDelegateToAutoRepublishFailedEvents() {
        new PaymentCompensationScheduler(paymentTaskCenterService).republishFailedEvents();

        verify(paymentTaskCenterService).runAutoRepublishFailedEvents();
    }

    @Test
    void shouldDelegateToAutoGenerateQueryMonitorAlerts() {
        new PaymentCompensationScheduler(paymentTaskCenterService).generateQueryMonitorAlerts();

        verify(paymentTaskCenterService).runAutoGenerateQueryMonitorAlerts();
    }

    @Test
    void shouldDispatchIssueAlertsWhenAutoDispatchEnabled() {
        new PaymentCompensationScheduler(paymentTaskCenterService, false, false, true)
                .dispatchIssueAlerts();

        verify(paymentTaskCenterService).runAutoDispatchIssueAlerts();
    }

    @Test
    void shouldSkipIssueAlertDispatchWhenAutoDispatchDisabled() {
        new PaymentCompensationScheduler(paymentTaskCenterService, false, false, false)
                .dispatchIssueAlerts();

        verify(paymentTaskCenterService, never()).runAutoDispatchIssueAlerts();
    }

    @Test
    void shouldReconcileIssueAlertReceiptsWhenExplicitlyEnabled() {
        new PaymentCompensationScheduler(
                paymentTaskCenterService, false, false, false, false, false, true, true)
                .reconcileIssueAlertReceipts();

        verify(paymentTaskCenterService).runAutoReconcileIssueAlertReceipts();
    }

    @Test
    void shouldRequireQueryCapabilityForAutomaticIssueAlertReceiptReconcile() {
        new PaymentCompensationScheduler(
                paymentTaskCenterService, false, false, false, false, false, true, false)
                .reconcileIssueAlertReceipts();

        verify(paymentTaskCenterService, never()).runAutoReconcileIssueAlertReceipts();
    }

    @Test
    void shouldSkipIssueAlertReceiptReconcileByDefault() {
        new PaymentCompensationScheduler(paymentTaskCenterService).reconcileIssueAlertReceipts();

        verify(paymentTaskCenterService, never()).runAutoReconcileIssueAlertReceipts();
    }

    @Test
    void shouldDelegateToAutoRetryFailedRefunds() {
        new PaymentCompensationScheduler(paymentTaskCenterService).retryFailedRefunds();

        verify(paymentTaskCenterService).runAutoRetryFailedRefunds();
    }

    @Test
    void shouldDelegateToAutoEscalateOverdueIssues() {
        new PaymentCompensationScheduler(paymentTaskCenterService).escalateOverdueIssues();

        verify(paymentTaskCenterService).runAutoEscalateOverdueIssues();
    }

    @Test
    void shouldDelegateToAutoControlPolicySelfChecks() {
        new PaymentCompensationScheduler(paymentTaskCenterService).runControlPolicySelfChecks();

        verify(paymentTaskCenterService).runAutoControlPolicySelfChecks();
    }
}
