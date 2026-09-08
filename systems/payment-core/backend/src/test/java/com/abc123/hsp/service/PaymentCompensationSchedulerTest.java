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
