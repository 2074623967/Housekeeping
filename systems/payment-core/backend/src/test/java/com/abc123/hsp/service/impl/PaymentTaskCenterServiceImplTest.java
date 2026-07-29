package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentConfigService;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付任务中心服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentTaskCenterServiceImplTest {

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;
    @Mock
    private PaymentExpiryTaskService paymentExpiryTaskService;
    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private RefundMapper refundMapper;
    @Mock
    private PaymentConfigService paymentConfigService;
    @Mock
    private PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService;

    @Test
    void shouldRunCloseExpiredPayments() {
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(2);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runCloseExpiredPayments();

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRunRepublishFailedEvents() {
        when(paymentEventMapper.findFailedEventNos()).thenReturn(Arrays.asList("EVT-1", "EVT-2"));
        when(paymentEventMapper.markRepublished("EVT-1")).thenReturn(1);
        when(paymentEventMapper.markRepublished("EVT-2")).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runRepublishFailedEvents();

        verify(paymentEventMapper).findFailedEventNos();
        verify(paymentEventMapper).markRepublished("EVT-1");
        verify(paymentEventMapper).markRepublished("EVT-2");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "P2".equals(entity.getSeverityLevel())
                        && "纳入当班跟进".equals(entity.getEscalationStatus())
                        && entity.getSuggestedAction().contains("复核下游是否完成收口")
        ));
    }

    @Test
    void shouldRunAutoRepublishFailedEvents() {
        when(paymentEventMapper.findFailedEventNos()).thenReturn(Collections.singletonList("EVT-AUTO-1"));
        when(paymentEventMapper.markRepublished("EVT-AUTO-1")).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EVENT_RETRY", "payment-event-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoRepublishFailedEvents();

        verify(paymentEventMapper).findFailedEventNos();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "payment-event-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease("PAYMENT_EVENT_RETRY", "payment-event-scheduler");
    }

    @Test
    void shouldRunAutoCloseExpiredPayments() {
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EXPIRE_CLOSE", "payment-expiry-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoCloseExpiredPayments();

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
        verify(paymentTaskCenterMapper).releaseTaskLease("PAYMENT_EXPIRE_CLOSE", "payment-expiry-scheduler");
    }

    @Test
    void shouldRunAutoRetryFailedRefunds() {
        when(refundMapper.findFailedRefundOrderIds()).thenReturn(Collections.singletonList("REF-AUTO-1"));
        when(refundMapper.updateRefundStatus("REF-AUTO-1", "FAIL", "PROCESSING", "warn", false)).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("REFUND_FAIL_RETRY", "refund-retry-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoRetryFailedRefunds();

        verify(refundMapper).findFailedRefundOrderIds();
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.any());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "refund-retry-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease("REFUND_FAIL_RETRY", "refund-retry-scheduler");
    }

    @Test
    void shouldRunIssueSlaEscalationTask() {
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(3);
        PaymentIssueAlertCandidateDTO candidate = new PaymentIssueAlertCandidateDTO();
        candidate.setIssueNo("ISSUE-WAIT-PAY-001");
        candidate.setPaymentOrderId("PAY-001");
        candidate.setIssueType("待回调未收口");
        candidate.setSeverity("P1");
        candidate.setResponsibilityGroup("支付后端值班组");
        candidate.setReceiver("支付后端值班");
        candidate.setEscalationReceiver("支付技术负责人");
        candidate.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        candidate.setEscalationTimeoutMinutes(30);
        candidate.setScheduleTag("交易链路白班");
        candidate.setEffectiveWindow("00:00-23:00");
        candidate.setAlertContent("支付异常 ISSUE-WAIT-PAY-001 已超过 P1 SLA，请进入异常中心处理。");
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates()).thenReturn(Collections.singletonList(candidate));
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_ISSUE_ESCALATE".equals(entity.getTaskCode())
                        && "P1".equals(entity.getSeverityLevel())
                        && "升级值班负责人".equals(entity.getEscalationStatus())
                        && entity.getSuggestedAction().contains("异常中心")
        ));
        verify(paymentTaskCenterMapper).insertIssueAlertLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PIA".equals(entity.getAlertNo().substring(0, 3))
                        && "待确认".equals(entity.getAckStatus())
                        && "IN_APP_OUTBOX".equals(entity.getAlertChannel())
                        && entity.getAlertContent().contains("交易链路白班")
                        && entity.getAlertContent().contains("00:00-23:00")
                        && entity.getAlertContent().contains("支付技术负责人")
                        && entity.getAlertContent().contains("30分钟未确认升级")
        ));
    }

    @Test
    void shouldGenerateEscalationAlertWhenIssueAlertUnacknowledgedOverTimeout() {
        PaymentIssueAlertCandidateDTO escalationCandidate = new PaymentIssueAlertCandidateDTO();
        escalationCandidate.setIssueNo("ISSUE-WAIT-PAY-001");
        escalationCandidate.setSourceAlertNo("PIA-SOURCE-001");
        escalationCandidate.setPaymentOrderId("PAY-001");
        escalationCandidate.setIssueType("待回调未收口");
        escalationCandidate.setSeverity("P1");
        escalationCandidate.setResponsibilityGroup("支付后端值班组");
        escalationCandidate.setReceiver("支付技术负责人");
        escalationCandidate.setEscalationReceiver("支付技术负责人");
        escalationCandidate.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        escalationCandidate.setEscalationTimeoutMinutes(30);
        escalationCandidate.setScheduleTag("交易链路白班");
        escalationCandidate.setEffectiveWindow("00:00-23:00");
        escalationCandidate.setAlertContent("升级来源告警 PIA-SOURCE-001 已超过 30 分钟未确认，请升级跟进。");
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(0);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates()).thenReturn(Collections.singletonList(escalationCandidate));
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues();

        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        verify(paymentTaskCenterMapper).insertIssueAlertLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "支付技术负责人".equals(entity.getReceiver())
                        && "待确认".equals(entity.getAckStatus())
                        && entity.getAlertContent().contains("PIA-SOURCE-001")
                        && entity.getAlertContent().contains("30分钟未确认升级")
        ));
    }

    @Test
    void shouldRunAutoIssueSlaEscalationTask() {
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(1);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_ISSUE_ESCALATE", "payment-issue-sla-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoEscalateOverdueIssues();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode())
                        && "payment-issue-sla-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease("PAYMENT_ISSUE_ESCALATE", "payment-issue-sla-scheduler");
    }

    @Test
    void shouldSkipAutoCloseExpiredPaymentsWhenLeaseHeldByAnotherInstance() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EXPIRE_CLOSE", "payment-expiry-scheduler", 120)).thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoCloseExpiredPayments();

        verify(paymentTaskCenterMapper).initTaskLease("PAYMENT_EXPIRE_CLOSE");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_EXPIRE_CLOSE".equals(entity.getTaskCode())
                        && "其他实例执行中".equals(entity.getEscalationStatus())
        ));
        verify(paymentExpiryTaskService, org.mockito.Mockito.never()).closeExpiredPayments();
        Assertions.assertTrue(result.getSummaryComment().contains("任务租约"));
    }

    @Test
    void shouldRunAutoControlPolicySelfChecksWithLease() {
        PaymentControlPolicySelfCheckSummaryDTO summary = new PaymentControlPolicySelfCheckSummaryDTO();
        summary.setProcessedCount(2);
        summary.setPassCount(1);
        summary.setWarnCount(1);
        summary.setFailCount(0);
        when(paymentConfigService.runAllEnabledControlPolicySelfChecks()).thenReturn(summary);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_CONTROL_SELF_CHECK", "payment-control-self-check-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoControlPolicySelfChecks();

        verify(paymentConfigService).runAllEnabledControlPolicySelfChecks();
        verify(paymentTaskCenterMapper).releaseTaskLease("PAYMENT_CONTROL_SELF_CHECK", "payment-control-self-check-scheduler");
    }

    @Test
    void shouldListTaskRunsWithDerivedFields() {
        PaymentTaskRunLogItemDTO item = new PaymentTaskRunLogItemDTO();
        item.setTaskCode("PAYMENT_EVENT_RETRY");
        item.setProcessedCount(2);
        item.setFailCount(1);
        when(paymentTaskCenterMapper.countTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(paymentTaskCenterMapper.findTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Collections.singletonList(item));

        PaymentTaskRunLogQueryDTO query = new PaymentTaskRunLogQueryDTO();
        query.setPageNo(0);
        query.setPageSize(200);
        PageResultDTO<PaymentTaskRunLogItemDTO> result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).listTaskRuns(query);

        Assertions.assertEquals(1L, result.getTotal());
        Assertions.assertEquals(1, result.getPageNo());
        Assertions.assertEquals(100, result.getPageSize());
        Assertions.assertEquals("P2", result.getItems().get(0).getSeverityLevel());
        Assertions.assertEquals("纳入当班跟进", result.getItems().get(0).getEscalationStatus());
        Assertions.assertEquals("/payment-events", result.getItems().get(0).getRecommendedRoute());
    }

    @Test
    void shouldEscalateImmediatelyWhenRefundRetryKeepsFailing() {
        when(refundMapper.findFailedRefundOrderIds()).thenReturn(Arrays.asList("REF-1", "REF-2"));
        when(refundMapper.updateRefundStatus("REF-1", "FAIL", "PROCESSING", "warn", false)).thenReturn(0);
        when(refundMapper.updateRefundStatus("REF-2", "FAIL", "PROCESSING", "warn", false)).thenReturn(0);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runRetryFailedRefunds();

        ArgumentCaptor<PaymentTaskRunLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentTaskRunLogEntity.class);
        verify(paymentTaskCenterMapper).insertTaskRunLog(logCaptor.capture());
        Assertions.assertEquals("P1", logCaptor.getValue().getSeverityLevel());
        Assertions.assertEquals("升级值班负责人", logCaptor.getValue().getEscalationStatus());
        Assertions.assertTrue(logCaptor.getValue().getSuggestedAction().contains("优先核对退款渠道响应"));
    }

    @Test
    void shouldRunControlPolicySelfCheckTask() {
        PaymentControlPolicySelfCheckSummaryDTO summary = new PaymentControlPolicySelfCheckSummaryDTO();
        summary.setProcessedCount(3);
        summary.setPassCount(2);
        summary.setWarnCount(1);
        summary.setFailCount(0);
        when(paymentConfigService.runAllEnabledControlPolicySelfChecks()).thenReturn(summary);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runControlPolicySelfChecks();

        verify(paymentConfigService).runAllEnabledControlPolicySelfChecks();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_CONTROL_SELF_CHECK".equals(entity.getTaskCode())
                        && "P2".equals(entity.getSeverityLevel())
                        && "纳入当班跟进".equals(entity.getEscalationStatus())
                        && entity.getSuggestedAction().contains("控制策略告警")
        ));
    }

    @Test
    void shouldRunAutoControlPolicySelfCheckTask() {
        PaymentControlPolicySelfCheckSummaryDTO summary = new PaymentControlPolicySelfCheckSummaryDTO();
        summary.setProcessedCount(1);
        summary.setPassCount(1);
        summary.setWarnCount(0);
        summary.setFailCount(0);
        when(paymentConfigService.runAllEnabledControlPolicySelfChecks()).thenReturn(summary);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_CONTROL_SELF_CHECK", "payment-control-self-check-scheduler", 120)).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoControlPolicySelfChecks();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode())
                        && "payment-control-self-check-scheduler".equals(entity.getTriggeredBy())
                        && "/payment-config".equals(entity.getRecommendedRoute())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease("PAYMENT_CONTROL_SELF_CHECK", "payment-control-self-check-scheduler");
    }

    @Test
    void shouldDispatchIssueAlerts() {
        when(paymentIssueAlertDeliveryService.dispatchPendingAlerts()).thenReturn(new com.abc123.hsp.dto.PaymentTaskActionResultDTO());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runDispatchIssueAlerts();

        verify(paymentIssueAlertDeliveryService).dispatchPendingAlerts();
        Assertions.assertNotNull(result.getOverview());
    }

    @Test
    void shouldAutoDispatchIssueAlerts() {
        when(paymentIssueAlertDeliveryService.autoDispatchPendingAlerts()).thenReturn(new com.abc123.hsp.dto.PaymentTaskActionResultDTO());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoDispatchIssueAlerts();

        verify(paymentIssueAlertDeliveryService).autoDispatchPendingAlerts();
        Assertions.assertNotNull(result.getOverview());
    }
}
