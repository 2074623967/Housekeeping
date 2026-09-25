package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentQueryTrendPointDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentDayEndAutoRunResultDTO;
import com.abc123.hsp.dto.PaymentDayEndPatrolResultDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.dto.PaymentEventRetryResultDTO;
import com.abc123.hsp.dto.RefundRetryResultDTO;
import com.abc123.hsp.dto.RefundQueryTaskResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentConfigService;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import com.abc123.hsp.service.PaymentDayEndService;
import com.abc123.hsp.service.PaymentEventRetryTaskService;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.RefundRetryTaskService;
import com.abc123.hsp.service.RefundQueryTaskService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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
    private PaymentDayEndService paymentDayEndService;
    @Mock
    private PaymentEventRetryTaskService paymentEventRetryTaskService;
    @Mock
    private RefundRetryTaskService refundRetryTaskService;
    @Mock
    private RefundQueryTaskService refundQueryTaskService;
    @Mock
    private PaymentConfigService paymentConfigService;
    @Mock
    private PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldExposeRetryableAndDeadLetterEventsAsImmediateP1Alert() {
        PaymentTaskCenterOverviewDTO summary = new PaymentTaskCenterOverviewDTO();
        summary.setFailedEventCount(5);
        summary.setRetryableFailedEventCount(2);
        summary.setDeadLetterEventCount(3);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(summary);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(2, overview.getRetryableFailedEventCount());
        Assertions.assertEquals(3, overview.getDeadLetterEventCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_EVENT".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("可批量重发 2 条")
                        && alert.getAlertMessage().contains("死信人工处置 3 条")
                        && "/payment-events?publishStatus=DEAD_LETTER"
                        .equals(alert.getActionRoute())));
    }

    @Test
    void shouldReturnTaskOverviewWhenSummaryQueryHasNoRow() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(null);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findRecentChannelQueryTrends(anyInt())).thenReturn(null);

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertNotNull(overview);
        Assertions.assertNotNull(overview.getFocusAlerts());
    }

    @Test
    void shouldReturnEmptyTaskRunsWhenMapperListsReturnNull() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(null);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(null);
        when(paymentTaskCenterMapper.countTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(paymentTaskCenterMapper.findTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        PaymentTaskCenterServiceImpl service = buildQueryTaskCenterService();
        Assertions.assertTrue(service.overview().getRecentTaskRuns().isEmpty());
        Assertions.assertTrue(service.listTaskRuns(new PaymentTaskRunLogQueryDTO()).getItems().isEmpty());
    }

    @Test
    void shouldIgnoreNullRowsInTaskOverviewSources() {
        PaymentQueryTrendPointDTO trend = new PaymentQueryTrendPointDTO();
        trend.setQueryCount(2);
        trend.setFailureCount(1);
        PaymentTaskRunLogItemDTO taskRun = new PaymentTaskRunLogItemDTO();
        taskRun.setTaskCode("PAYMENT_EVENT_RETRY");
        taskRun.setProcessedCount(1);
        when(paymentTaskCenterMapper.findRecentChannelQueryTrends(24))
                .thenReturn(Arrays.asList(null, trend));
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Arrays.asList(null, taskRun));

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(1, overview.getQueryTrends().size());
        Assertions.assertEquals("50.00%", overview.getQueryTrends().get(0).getFailureRate());
        Assertions.assertEquals(1, overview.getRecentTaskRuns().size());
    }

    @Test
    void shouldExposeRetryableAndBlockedFailedRefundsAsImmediateP1Alert() {
        PaymentTaskCenterOverviewDTO summary = new PaymentTaskCenterOverviewDTO();
        summary.setFailedRefundCount(5);
        summary.setRetryableFailedRefundCount(2);
        summary.setBlockedFailedRefundCount(3);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(summary);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(2, overview.getRetryableFailedRefundCount());
        Assertions.assertEquals(3, overview.getBlockedFailedRefundCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "REFUND_RETRY".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("可批量重试 2 笔")
                        && alert.getAlertMessage().contains("终态事实异常 3 笔")
                        && "/refunds?refundStatus=TERMINAL_FACT_MISMATCH"
                        .equals(alert.getActionRoute())));
    }

    @Test
    void shouldExposePendingQueryBacklogMetricsInOverview() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countPendingPaymentQueryBacklog(30)).thenReturn(120);
        when(paymentTaskCenterMapper.countDuePendingPaymentQueries(30, 60, 2, 900)).thenReturn(35);
        when(refundQueryTaskService.countPendingRefunds()).thenReturn(18);
        when(refundQueryTaskService.countDueRefunds()).thenReturn(6);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(120, overview.getPendingQueryBacklogCount());
        Assertions.assertEquals(35, overview.getDuePendingQueryCount());
        Assertions.assertEquals(18, overview.getPendingRefundQueryBacklogCount());
        Assertions.assertEquals(6, overview.getDuePendingRefundQueryCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_QUERY_BACKLOG".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("35 笔当前已到自动查询时间")));
    }

    @Test
    void shouldExposeExpiryCloseBackoffMetricsInOverview() {
        PaymentTaskCenterOverviewDTO summary = new PaymentTaskCenterOverviewDTO();
        summary.setExpiredPaymentCount(9);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(summary);
        when(paymentTaskCenterMapper.countDueExpiredPayments()).thenReturn(6);
        when(paymentTaskCenterMapper.countBackingOffExpiredPayments()).thenReturn(3);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(6, overview.getDueExpiredPaymentCount());
        Assertions.assertEquals(3, overview.getBackingOffExpiredPaymentCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_EXPIRY".equals(alert.getAlertType())
                        && alert.getAlertMessage().contains("可执行 6 笔")
                        && alert.getAlertMessage().contains("失败退避中 3 笔")));
    }

    @Test
    void shouldExposeIssueAlertReceiptQueryQueueMetricsInOverview() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countPendingIssueAlertReceipts()).thenReturn(13);
        when(paymentTaskCenterMapper.countManualIssueAlertReceipts(30)).thenReturn(11);
        when(paymentTaskCenterMapper.countDueIssueAlertReceipts(60, 10, 30)).thenReturn(7);
        when(paymentTaskCenterMapper.countBackingOffIssueAlertReceipts(60, 10, 30)).thenReturn(5);
        when(paymentTaskCenterMapper.countQueryingIssueAlertReceipts(30)).thenReturn(2);
        when(paymentTaskCenterMapper.countExhaustedIssueAlertReceipts(10, 30)).thenReturn(3);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(13, overview.getPendingIssueAlertReceiptCount());
        Assertions.assertEquals(11, overview.getManualIssueAlertReceiptCount());
        Assertions.assertEquals(7, overview.getDueIssueAlertReceiptCount());
        Assertions.assertEquals(5, overview.getBackingOffIssueAlertReceiptCount());
        Assertions.assertEquals(2, overview.getQueryingIssueAlertReceiptCount());
        Assertions.assertEquals(3, overview.getExhaustedIssueAlertReceiptCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG".equals(alert.getAlertType())
                        && alert.getAlertMessage().contains("人工可执行 11 条")
                        && alert.getAlertMessage().contains("自动到期 7 条")));
    }

    @Test
    void shouldExposeIssueSlaRiskAndActionCandidatesSeparately() {
        PaymentTaskCenterOverviewDTO summary = new PaymentTaskCenterOverviewDTO();
        summary.setOverdueIssueCount(6);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(summary);
        when(paymentTaskCenterMapper.countPendingIssueAlertGenerationCandidates()).thenReturn(2);
        when(paymentTaskCenterMapper.countPendingIssueAlertEscalationCandidates()).thenReturn(3);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(6, overview.getOverdueIssueCount());
        Assertions.assertEquals(2, overview.getPendingIssueAlertGenerationCount());
        Assertions.assertEquals(3, overview.getPendingIssueAlertEscalationCount());
        Assertions.assertEquals(5, overview.getActionableIssueSlaCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_SLA".equals(alert.getAlertType())
                        && Integer.valueOf(6).equals(alert.getAffectedCount())
                        && alert.getAlertMessage().contains("待首次告警 2 条")
                        && alert.getAlertMessage().contains("待升级告警 3 条")));
    }

    @Test
    void shouldRunSpecifiedIssueAlertReceiptQueryAndRefreshOverview() {
        PaymentTaskActionResultDTO actionResult = new PaymentTaskActionResultDTO();
        actionResult.setProcessedCount(1);
        when(paymentIssueAlertDeliveryService.reconcileDeliveryReceipt("PIA-IM-001", "支付运营"))
                .thenReturn(actionResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService()
                .runReconcileIssueAlertReceipt("PIA-IM-001", "支付运营");

        verify(paymentIssueAlertDeliveryService).reconcileDeliveryReceipt("PIA-IM-001", "支付运营");
        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertNotNull(result.getOverview());
    }

    @Test
    void shouldExposeUnknownRefundSubmitAsImmediateP1Alert() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countUnknownRefundSubmitEvents()).thenReturn(2);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(2, overview.getUnknownRefundSubmitEventCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "REFUND_SUBMIT_UNKNOWN".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && "danger".equals(alert.getAlertLevelType())
                        && alert.getAlertMessage().contains("禁止直接重发")
                        && "/payment-issues?issueType=退款提交结果待核查"
                        .equals(alert.getActionRoute())));
    }

    @Test
    void shouldExposeQueryFailureTrendAndRaiseRateThresholdAlert() {
        PaymentQueryTrendPointDTO trend = new PaymentQueryTrendPointDTO();
        trend.setTimeBucket("2026-09-08 09:00");
        trend.setQueryCount(3);
        trend.setFailureCount(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countRecentChannelQueries(24)).thenReturn(12);
        when(paymentTaskCenterMapper.countRecentChannelQueryFailures(24)).thenReturn(4);
        when(paymentTaskCenterMapper.findRecentChannelQueryTrends(24))
                .thenReturn(Collections.singletonList(trend));
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(24, overview.getQueryMonitorWindowHours());
        Assertions.assertEquals(12, overview.getRecentQueryCount());
        Assertions.assertEquals(4, overview.getRecentQueryFailureCount());
        Assertions.assertEquals("33.33%", overview.getRecentQueryFailureRate());
        Assertions.assertEquals(1, overview.getActionableQueryMonitorAlertCount());
        Assertions.assertEquals("33.33%", overview.getQueryTrends().get(0).getFailureRate());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_QUERY_FAILURE_TREND".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("近 24 小时支付与退款查单 12 次")));
    }

    @Test
    void shouldExposeRateLimitRejectionsAndRaiseIndependentThresholdAlert() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countRecentChannelQueryRateLimitRejections(24)).thenReturn(10);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(10, overview.getRecentQueryRateLimitRejectionCount());
        Assertions.assertEquals(10, overview.getQueryRateLimitRejectionCountThreshold());
        Assertions.assertEquals(1, overview.getActionableQueryMonitorAlertCount());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_QUERY_RATE_LIMIT_REJECTION".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("共有 10 次支付或退款查单")));
    }

    @Test
    void shouldExcludeQueryMonitorAlertsSuppressedByCooldownFromActionCandidates() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countRecentChannelQueries(24)).thenReturn(12);
        when(paymentTaskCenterMapper.countRecentChannelQueryFailures(24)).thenReturn(4);
        when(paymentTaskCenterMapper.countRecentChannelQueryRateLimitRejections(24)).thenReturn(10);
        when(paymentTaskCenterMapper.countRecentUnacknowledgedQueryMonitorAlerts(
                "主动查单失败趋势", 60)).thenReturn(1);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(1, overview.getActionableQueryMonitorAlertCount());
        verify(paymentTaskCenterMapper).countRecentUnacknowledgedQueryMonitorAlerts(
                "主动查单失败趋势", 60);
        verify(paymentTaskCenterMapper).countRecentUnacknowledgedQueryMonitorAlerts(
                "主动查单限流拒绝", 60);
    }

    @Test
    void shouldExposeIssueAlertDeliveryBacklogsAndRaiseIndependentAlerts() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countPendingIssueAlertDispatches()).thenReturn(3);
        when(paymentTaskCenterMapper.countFailedIssueAlertDispatches()).thenReturn(1);
        when(paymentTaskCenterMapper.findOldestPendingIssueAlertDispatchMinutes()).thenReturn(8);
        when(paymentTaskCenterMapper.countPendingIssueAlertReceipts()).thenReturn(2);
        when(paymentTaskCenterMapper.findOldestPendingIssueAlertReceiptMinutes()).thenReturn(20);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertEquals(3, overview.getPendingIssueAlertDispatchCount());
        Assertions.assertEquals(1, overview.getFailedIssueAlertDispatchCount());
        Assertions.assertEquals(8, overview.getOldestPendingIssueAlertDispatchMinutes());
        Assertions.assertEquals(10, overview.getIssueAlertDispatchCountThreshold());
        Assertions.assertEquals(15, overview.getIssueAlertDispatchTimeoutMinutes());
        Assertions.assertEquals(2, overview.getPendingIssueAlertReceiptCount());
        Assertions.assertEquals(20, overview.getOldestPendingIssueAlertReceiptMinutes());
        Assertions.assertEquals(10, overview.getIssueAlertReceiptCountThreshold());
        Assertions.assertEquals(30, overview.getIssueAlertReceiptTimeoutMinutes());
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("其中 1 条仍存在派发异常")
                        && ("/payment-issue-alerts?alertChannel=IN_APP_OUTBOX"
                        + "&alertStatus=PENDING_DISPATCH&ackStatus=待确认").equals(alert.getActionRoute())));
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG".equals(alert.getAlertType())
                        && "P2".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("2 条供应商已受理告警")
                        && "/payment-issue-alerts?receiptQueryState=PENDING_RECEIPT"
                        .equals(alert.getActionRoute())));
    }

    @Test
    void shouldRaiseP1WhenSingleIssueAlertBacklogExceedsAgeThreshold() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countPendingIssueAlertDispatches()).thenReturn(1);
        when(paymentTaskCenterMapper.findOldestPendingIssueAlertDispatchMinutes()).thenReturn(15);
        when(paymentTaskCenterMapper.countPendingIssueAlertReceipts()).thenReturn(1);
        when(paymentTaskCenterMapper.findOldestPendingIssueAlertReceiptMinutes()).thenReturn(30);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("最老已等待 15 分钟")));
        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("最老已等待 30 分钟")));
    }

    @Test
    void shouldRaiseP1AndLinkToAlertDetailsWhenReceiptQueriesAreExhausted() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.countPendingIssueAlertReceipts()).thenReturn(1);
        when(paymentTaskCenterMapper.countExhaustedIssueAlertReceipts(10, 30)).thenReturn(1);
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskCenterOverviewDTO overview = buildQueryTaskCenterService().overview();

        Assertions.assertTrue(overview.getFocusAlerts().stream().anyMatch(alert ->
                "PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG".equals(alert.getAlertType())
                        && "P1".equals(alert.getAlertLevel())
                        && alert.getAlertMessage().contains("自动次数耗尽 1 条")
                        && "/payment-issue-alerts?receiptQueryState=EXHAUSTED".equals(alert.getActionRoute())));
    }

    @Test
    void shouldQueryPendingPaymentsAndIsolateSinglePaymentFailure() {
        when(paymentTaskCenterMapper.findPendingPaymentOrderIdsForManualQuery(30, 20, 100))
                .thenReturn(Arrays.asList("PAY-QUERY-1", "PAY-QUERY-2", "PAY-QUERY-3"));
        when(paymentService.query(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("支付运营")))
                .thenAnswer(invocation -> {
                    com.abc123.hsp.dto.PaymentQueryRequestDTO request = invocation.getArgument(0);
                    if ("PAY-QUERY-3".equals(request.getPaymentOrderId())) {
                        throw new IllegalStateException("gateway unavailable");
                    }
                    PaymentDetailDTO detail = new PaymentDetailDTO();
                    detail.setStatus("PAY-QUERY-1".equals(request.getPaymentOrderId())
                            ? "SUCCESS"
                            : "WAIT_CALLBACK");
                    return detail;
                });
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService()
                .runPendingPaymentQueries(" 支付运营 ");

        Assertions.assertEquals(3, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
        verify(paymentTaskCenterMapper).findPendingPaymentOrderIdsForManualQuery(30, 20, 100);
        verify(paymentService, org.mockito.Mockito.times(3)).query(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("支付运营"));
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_PENDING_QUERY".equals(entity.getTaskCode())
                        && "P2".equals(entity.getSeverityLevel())
                        && "支付运营".equals(entity.getTriggeredBy())
                        && "/payments".equals(entity.getRecommendedRoute())
                        && entity.getSummaryComment().contains("查询失败 1 笔")
                        && entity.getSummaryComment().contains("PAY-QUERY-3")
        ));
    }

    @Test
    void shouldRejectInvalidManualTaskOperatorBeforeReadingCandidates() {
        PaymentTaskCenterServiceImpl service = buildQueryTaskCenterService();

        IllegalArgumentException blankException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.runPendingPaymentQueries(" "));
        IllegalArgumentException oversizedException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.runPendingPaymentQueries(
                        String.join("", Collections.nCopies(65, "A"))));
        IllegalArgumentException oversizedAfterTrimException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.runPendingPaymentQueries(
                        String.join("", Collections.nCopies(64, "A")) + " "));
        IllegalArgumentException controlException = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.runPendingPaymentQueries("finance-ops\u0085"));

        Assertions.assertTrue(blankException.getMessage().contains("人工任务操作人不能为空"));
        Assertions.assertTrue(oversizedException.getMessage().contains("不能超过64个字符"));
        Assertions.assertTrue(oversizedAfterTrimException.getMessage().contains("不能超过64个字符"));
        Assertions.assertTrue(controlException.getMessage().contains("不能包含控制字符"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .findPendingPaymentOrderIdsForManualQuery(
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldUseLeaseForAutomaticPendingPaymentQueries() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_PENDING_QUERY"), leaseOwner("payment-query-scheduler"), eq(120))).thenReturn(1);
        when(paymentTaskCenterMapper.findDuePendingPaymentOrderIdsForQuery(30, 60, 2, 900, 20, 100))
                .thenReturn(Collections.singletonList("PAY-AUTO-QUERY-1"));
        when(paymentService.query(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("payment-query-scheduler")))
                .thenReturn(new PaymentDetailDTO());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        buildQueryTaskCenterService().runAutoPendingPaymentQueries();

        verify(paymentTaskCenterMapper).initTaskLease("PAYMENT_PENDING_QUERY");
        verify(paymentTaskCenterMapper).findDuePendingPaymentOrderIdsForQuery(30, 60, 2, 900, 20, 100);
        verify(paymentService).query(
                org.mockito.ArgumentMatchers.argThat(
                        request -> "PAY-AUTO-QUERY-1".equals(request.getPaymentOrderId())),
                org.mockito.ArgumentMatchers.eq("payment-query-scheduler"));
        ArgumentCaptor<String> leaseOwnerCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentTaskCenterMapper).acquireTaskLease(
                eq("PAYMENT_PENDING_QUERY"), leaseOwnerCaptor.capture(), eq(120));
        verify(paymentTaskCenterMapper).releaseTaskLease(
                eq("PAYMENT_PENDING_QUERY"), leaseOwnerCaptor.capture());
        Assertions.assertEquals(leaseOwnerCaptor.getAllValues().get(0), leaseOwnerCaptor.getAllValues().get(1));
        Assertions.assertTrue(leaseOwnerCaptor.getValue().startsWith("payment-query-scheduler:"));
    }

    @Test
    void shouldClampGlobalAndPerChannelPaymentQueryBatchSizes() {
        when(paymentTaskCenterMapper.findPendingPaymentOrderIdsForManualQuery(30, 1, 1))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findPendingPaymentOrderIdsForManualQuery(30, 1000, 1000))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        buildQueryTaskCenterService(0, 0).runPendingPaymentQueries("payment-core-admin");
        buildQueryTaskCenterService(1001, 1001).runPendingPaymentQueries("payment-core-admin");

        verify(paymentTaskCenterMapper).findPendingPaymentOrderIdsForManualQuery(30, 1, 1);
        verify(paymentTaskCenterMapper).findPendingPaymentOrderIdsForManualQuery(30, 1000, 1000);
    }

    @Test
    void shouldQueryPendingRefundsAndRecordTaskResult() {
        RefundQueryTaskResultDTO queryResult = new RefundQueryTaskResultDTO();
        queryResult.setProcessedCount(3);
        queryResult.setSuccessCount(1);
        queryResult.setWarningCount(1);
        queryResult.setFailCount(1);
        queryResult.getFailedRefundOrderIds().add("REF-QUERY-3");
        when(refundQueryTaskService.queryPendingRefunds("payment-core-admin")).thenReturn(queryResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService()
                .runPendingRefundQueries("payment-core-admin");

        Assertions.assertEquals(3, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertEquals(1, result.getFailCount());
        verify(refundQueryTaskService).queryPendingRefunds("payment-core-admin");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "REFUND_PENDING_QUERY".equals(entity.getTaskCode())
                        && "P2".equals(entity.getSeverityLevel())
                        && "/refunds".equals(entity.getRecommendedRoute())
                        && entity.getSummaryComment().contains("REF-QUERY-3")));
    }

    @Test
    void shouldUseLeaseForAutomaticPendingRefundQueries() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("REFUND_PENDING_QUERY"), leaseOwner("refund-query-scheduler"), eq(120))).thenReturn(1);
        when(refundQueryTaskService.queryDueRefunds("refund-query-scheduler"))
                .thenReturn(new RefundQueryTaskResultDTO());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        buildQueryTaskCenterService().runAutoPendingRefundQueries();

        verify(paymentTaskCenterMapper).initTaskLease("REFUND_PENDING_QUERY");
        verify(refundQueryTaskService).queryDueRefunds("refund-query-scheduler");
        ArgumentCaptor<String> leaseOwnerCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentTaskCenterMapper).acquireTaskLease(
                eq("REFUND_PENDING_QUERY"), leaseOwnerCaptor.capture(), eq(120));
        verify(paymentTaskCenterMapper).releaseTaskLease(
                eq("REFUND_PENDING_QUERY"), leaseOwnerCaptor.capture());
        Assertions.assertEquals(leaseOwnerCaptor.getAllValues().get(0), leaseOwnerCaptor.getAllValues().get(1));
    }

    @Test
    void shouldGenerateQueryMonitorOutboxAndSuppressRecentDuplicate() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_QUERY_MONITOR_ALERT"), leaseOwner("payment-core-admin"), eq(120))).thenReturn(1);
        when(paymentTaskCenterMapper.countRecentChannelQueries(24)).thenReturn(12);
        when(paymentTaskCenterMapper.countRecentChannelQueryFailures(24)).thenReturn(4);
        when(paymentTaskCenterMapper.countRecentChannelQueryRateLimitRejections(24)).thenReturn(10);
        when(paymentTaskCenterMapper.countRecentUnacknowledgedQueryMonitorAlerts(
                "主动查单失败趋势", 60)).thenReturn(0);
        when(paymentTaskCenterMapper.countRecentUnacknowledgedQueryMonitorAlerts(
                "主动查单限流拒绝", 60)).thenReturn(1);
        when(paymentTaskCenterMapper.insertIssueAlertLog(
                org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService()
                .runGenerateQueryMonitorAlerts("payment-core-admin");

        Assertions.assertEquals(2, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertTrue(result.getSummaryComment().contains("冷却窗口抑制 1 条"));
        ArgumentCaptor<PaymentIssueAlertLogEntity> alertCaptor =
                ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(alertCaptor.capture());
        PaymentIssueAlertLogEntity alert = alertCaptor.getValue();
        Assertions.assertEquals("主动查单失败趋势", alert.getIssueType());
        Assertions.assertEquals("IN_APP_OUTBOX", alert.getAlertChannel());
        Assertions.assertEquals("P1", alert.getSeverity());
        Assertions.assertEquals("支付渠道运营组", alert.getResponsibilityGroup());
        Assertions.assertTrue(alert.getAlertContent().contains("失败率 33.33%"));
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_QUERY_MONITOR_ALERT".equals(entity.getTaskCode())
                        && "MANUAL".equals(entity.getRunMode())
                        && "P1".equals(entity.getSeverityLevel())
                        && "/payment-issues".equals(entity.getRecommendedRoute())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease(
                eq("PAYMENT_QUERY_MONITOR_ALERT"), leaseOwner("payment-core-admin"));
    }

    @Test
    void shouldRunCloseExpiredPayments() {
        PaymentExpiryCloseResultDTO closeResult = new PaymentExpiryCloseResultDTO();
        closeResult.setProcessedCount(3);
        closeResult.setSuccessCount(2);
        closeResult.setSkippedCount(1);
        closeResult.setFailCount(1);
        closeResult.setFailedPaymentOrderIds(Collections.singletonList("PAY-FAIL"));
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(closeResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runCloseExpiredPayments("payment-core-admin");

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_EXPIRE_CLOSE".equals(entity.getTaskCode())
                        && entity.getProcessedCount() == 3
                        && entity.getSuccessCount() == 2
                        && entity.getWarningCount() == 1
                        && entity.getFailCount() == 1
                        && "P2".equals(entity.getSeverityLevel())
                        && entity.getSummaryComment().contains("并发跳过 1 笔")
                        && entity.getSummaryComment().contains("PAY-FAIL")
                        && entity.getSuggestedAction().contains("复跑任务")
        ));
    }

    @Test
    void shouldExportTaskRunsCsv() {
        PaymentTaskRunLogItemDTO item = new PaymentTaskRunLogItemDTO();
        item.setTaskLogNo("LOG-001");
        item.setTaskCode("PAYMENT_EVENT_RETRY");
        item.setTaskName("失败事件重发");
        item.setBizDate("2026-09-11");
        item.setBusinessRefNo("DEB-20260911-AUTO-001");
        item.setRunMode("MANUAL");
        item.setTaskStatus("SUCCESS");
        item.setSeverityLevel("P2");
        item.setEscalationStatus("纳入当班跟进");
        item.setProcessedCount(2);
        item.setSuccessCount(2);
        item.setWarningCount(0);
        item.setFailCount(0);
        item.setSummaryComment("已完成");
        item.setSuggestedAction("继续观察");
        item.setRecommendedRoute("/payment-events");
        item.setTriggeredBy("payment-core-admin");
        item.setStartedAt("2026-08-03 10:00:00");
        item.setCompletedAt("2026-08-03 10:00:03");
        when(paymentTaskCenterMapper.findTaskRunLogsForExport(org.mockito.ArgumentMatchers.any(PaymentTaskRunLogQueryDTO.class)))
                .thenReturn(Arrays.asList(null, item));

        String csv = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).exportTaskRunsCsv(new PaymentTaskRunLogQueryDTO());

        verify(paymentTaskCenterMapper).findTaskRunLogsForExport(org.mockito.ArgumentMatchers.any(PaymentTaskRunLogQueryDTO.class));
        Assertions.assertTrue(csv.contains("日志号,任务编码"));
        Assertions.assertTrue(csv.contains("\"LOG-001\""));
        Assertions.assertTrue(csv.contains("\"2026-09-11\""));
        Assertions.assertTrue(csv.contains("\"DEB-20260911-AUTO-001\""));
    }

    @Test
    void shouldRejectOversizedTaskRunQueryBeforeMapperAccess() {
        PaymentTaskRunLogQueryDTO taskCodeQuery = new PaymentTaskRunLogQueryDTO();
        taskCodeQuery.setTaskCode(repeat("T", 65));
        PaymentTaskRunLogQueryDTO taskCodeAfterTrimQuery = new PaymentTaskRunLogQueryDTO();
        taskCodeAfterTrimQuery.setTaskCode(repeat("T", 64) + " ");
        PaymentTaskRunLogQueryDTO runModeQuery = new PaymentTaskRunLogQueryDTO();
        runModeQuery.setRunMode(repeat("M", 33));
        PaymentTaskRunLogQueryDTO taskStatusQuery = new PaymentTaskRunLogQueryDTO();
        taskStatusQuery.setTaskStatus(repeat("S", 33));
        PaymentTaskRunLogQueryDTO severityQuery = new PaymentTaskRunLogQueryDTO();
        severityQuery.setSeverityLevel(repeat("P", 33));
        PaymentTaskCenterServiceImpl service = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(taskCodeQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(taskCodeAfterTrimQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.exportTaskRunsCsv(runModeQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(taskStatusQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.exportTaskRunsCsv(severityQuery));
        verifyNoInteractions(paymentTaskCenterMapper);
    }

    @Test
    void shouldRejectControlCharactersInTaskRunQueryBeforeMapperAccess() {
        PaymentTaskRunLogQueryDTO taskCodeQuery = new PaymentTaskRunLogQueryDTO();
        taskCodeQuery.setTaskCode("PAYMENT_EVENT_RETRY\n");
        PaymentTaskRunLogQueryDTO runModeQuery = new PaymentTaskRunLogQueryDTO();
        runModeQuery.setRunMode("MANUAL\n");
        PaymentTaskRunLogQueryDTO taskStatusQuery = new PaymentTaskRunLogQueryDTO();
        taskStatusQuery.setTaskStatus("SUCCESS\n");
        PaymentTaskRunLogQueryDTO severityQuery = new PaymentTaskRunLogQueryDTO();
        severityQuery.setSeverityLevel("P2\n");
        PaymentTaskCenterServiceImpl service = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        );

        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(taskCodeQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.exportTaskRunsCsv(runModeQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(taskStatusQuery));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.exportTaskRunsCsv(severityQuery));
        verifyNoInteractions(paymentTaskCenterMapper);
    }

    @Test
    void shouldNormalizeAndRejectInvalidTaskRunBusinessDate() {
        PaymentTaskRunLogQueryDTO validQuery = new PaymentTaskRunLogQueryDTO();
        validQuery.setBizDate(" 2026-09-11 ");
        when(paymentTaskCenterMapper.countTaskRunLogs(validQuery)).thenReturn(0L);
        when(paymentTaskCenterMapper.findTaskRunLogs(validQuery)).thenReturn(Collections.emptyList());
        PaymentTaskRunLogQueryDTO invalidQuery = new PaymentTaskRunLogQueryDTO();
        invalidQuery.setBizDate("2026-02-30");
        PaymentTaskCenterServiceImpl service = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        );

        service.listTaskRuns(validQuery);

        Assertions.assertEquals("2026-09-11", validQuery.getBizDate());
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.listTaskRuns(invalidQuery));
    }

    @Test
    void shouldRunRepublishFailedEvents() {
        PaymentEventRetryResultDTO retryResult = new PaymentEventRetryResultDTO();
        retryResult.setProcessedCount(3);
        retryResult.setSuccessCount(1);
        retryResult.setFailCount(2);
        retryResult.setFailedEventNos(Arrays.asList("EVT-2", "EVT-3"));
        when(paymentEventRetryTaskService.retryFailedEvents(false, "finance-ops")).thenReturn(retryResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runRepublishFailedEvents(" finance-ops ");

        verify(paymentEventRetryTaskService).retryFailedEvents(false, "finance-ops");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "P2".equals(entity.getSeverityLevel())
                        && "finance-ops".equals(entity.getTriggeredBy())
                        && "纳入当班跟进".equals(entity.getEscalationStatus())
                        && entity.getProcessedCount() == 3
                        && entity.getSuccessCount() == 1
                        && entity.getFailCount() == 2
                        && entity.getSummaryComment().contains("EVT-2/EVT-3")
                        && entity.getSuggestedAction().contains("重发表")
        ));
    }

    @Test
    void shouldRunAutoRepublishFailedEvents() {
        PaymentEventRetryResultDTO retryResult = new PaymentEventRetryResultDTO();
        retryResult.setProcessedCount(1);
        retryResult.setSuccessCount(1);
        when(paymentEventRetryTaskService.retryFailedEvents(true, "payment-event-scheduler")).thenReturn(retryResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_EVENT_RETRY"), leaseOwner("payment-event-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoRepublishFailedEvents();

        verify(paymentEventRetryTaskService).retryFailedEvents(true, "payment-event-scheduler");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "payment-event-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("PAYMENT_EVENT_RETRY"), leaseOwner("payment-event-scheduler"));
    }

    @Test
    void shouldRunAutoCloseExpiredPayments() {
        PaymentExpiryCloseResultDTO closeResult = new PaymentExpiryCloseResultDTO();
        closeResult.setProcessedCount(1);
        closeResult.setSuccessCount(1);
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(closeResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_EXPIRE_CLOSE"), leaseOwner("payment-expiry-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoCloseExpiredPayments();

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("PAYMENT_EXPIRE_CLOSE"), leaseOwner("payment-expiry-scheduler"));
    }

    @Test
    void shouldRecordWarningAutomaticDayEndInTaskCenter() {
        PaymentDayEndAutoRunResultDTO dayEndResult = dayEndResult(true, null, "WARNING");
        when(paymentDayEndService.runAutomatically()).thenReturn(dayEndResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEnd();

        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_DAY_END".equals(entity.getTaskCode())
                        && "AUTO".equals(entity.getRunMode())
                        && "2026-09-11".equals(entity.getBizDate())
                        && "DEB-20260911-AUTO-001".equals(entity.getBusinessRefNo())
                        && "payment-day-end-scheduler".equals(entity.getTriggeredBy())
                        && "WARNING".equals(entity.getTaskStatus())
                        && "P2".equals(entity.getSeverityLevel())
                        && "/payment-day-end".equals(entity.getRecommendedRoute())
                        && entity.getSummaryComment().contains("2026-09-11")
                        && entity.getSummaryComment().contains("WARNING")));
    }

    @Test
    void shouldRecordExistingBatchAsSuccessfulAutomaticDayEndSkip() {
        when(paymentDayEndService.runAutomatically())
                .thenReturn(dayEndResult(false, "EXISTING_BATCH", null));
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEnd();

        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getWarningCount());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "SUCCESS".equals(entity.getTaskStatus())
                        && "P3".equals(entity.getSeverityLevel())
                        && entity.getSummaryComment().contains("已存在日终批次")
                        && entity.getSuggestedAction().contains("无需重复生成")));
    }

    @Test
    void shouldRecordBusyLeaseAsAutomaticDayEndWarning() {
        when(paymentDayEndService.runAutomatically())
                .thenReturn(dayEndResult(false, "LEASE_BUSY", null));
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        buildQueryTaskCenterService().runAutoPaymentDayEnd();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "WARNING".equals(entity.getTaskStatus())
                        && "P2".equals(entity.getSeverityLevel())
                        && entity.getWarningCount() == 1
                        && entity.getSummaryComment().contains("其他实例持有")));
    }

    @Test
    void shouldRecordSanitizedAutomaticDayEndFailureAndRethrow() {
        IllegalStateException failure = new IllegalStateException("database-password-secret");
        String expectedBizDate = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
                .minusDays(1).toString();
        when(paymentDayEndService.runAutomatically()).thenThrow(failure);

        IllegalStateException thrown = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertSame(failure, thrown);
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_DAY_END".equals(entity.getTaskCode())
                        && "WARNING".equals(entity.getTaskStatus())
                        && expectedBizDate.equals(entity.getBizDate())
                        && entity.getBusinessRefNo() == null
                        && "P1".equals(entity.getSeverityLevel())
                        && entity.getFailCount() == 1
                        && "PAYMENT_DAY_END_AUTO_RUN_FAILED".equals(entity.getSummaryComment())
                        && !entity.getSummaryComment().contains("database-password-secret")));
    }

    @Test
    void shouldFailClosedAndRecordInvalidAutomaticDayEndResult() {
        when(paymentDayEndService.runAutomatically())
                .thenReturn(dayEndResult(false, "UNKNOWN_SKIP_REASON", null));

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertEquals("automatic day-end returned an invalid skipped result", error.getMessage());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_DAY_END_AUTO_RUN_FAILED".equals(entity.getSummaryComment())
                        && "P1".equals(entity.getSeverityLevel())
                        && entity.getFailCount() == 1));
    }

    @Test
    void shouldFailClosedWhenExecutedAutomaticDayEndHasNoBatchReference() {
        PaymentDayEndAutoRunResultDTO result = dayEndResult(true, null, "COMPLETED");
        result.setBatchNo(null);
        when(paymentDayEndService.runAutomatically()).thenReturn(result);

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertEquals("automatic day-end returned an invalid executed result", error.getMessage());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> entity.getBusinessRefNo() == null
                        && "PAYMENT_DAY_END_AUTO_RUN_FAILED".equals(entity.getSummaryComment())));
    }

    @Test
    void shouldFailClosedWhenExecutedAutomaticDayEndHasBlankBatchReference() {
        PaymentDayEndAutoRunResultDTO result = dayEndResult(true, null, "COMPLETED");
        result.setBatchNo("   ");
        when(paymentDayEndService.runAutomatically()).thenReturn(result);

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertEquals("automatic day-end returned an invalid executed result", error.getMessage());
    }

    @Test
    void shouldFailClosedWhenExecutedAutomaticDayEndBatchReferenceIsTooLong() {
        PaymentDayEndAutoRunResultDTO result = dayEndResult(true, null, "COMPLETED");
        result.setBatchNo(String.join("", Collections.nCopies(65, "B")));
        when(paymentDayEndService.runAutomatically()).thenReturn(result);

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertEquals("automatic day-end returned an invalid executed result", error.getMessage());
    }

    @Test
    void shouldFailClosedWhenSkippedAutomaticDayEndCarriesBatchReference() {
        PaymentDayEndAutoRunResultDTO result = dayEndResult(false, "EXISTING_BATCH", null);
        result.setBatchNo("DEB-20260911-AUTO-001");
        when(paymentDayEndService.runAutomatically()).thenReturn(result);

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEnd());

        Assertions.assertEquals("automatic day-end returned an invalid skipped result", error.getMessage());
    }

    @Test
    void shouldRaiseP1WhenPreviousBusinessDayHasNoDayEndBatch() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 0, 0));
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getFailCount());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> ("PTL-DAY-END-PATROL-" + bizDate.replace("-", ""))
                        .equals(entity.getTaskLogNo())
                        && "PAYMENT_DAY_END_PATROL".equals(entity.getTaskCode())
                        && bizDate.equals(entity.getBizDate())
                        && "P1".equals(entity.getSeverityLevel())
                        && "/payment-day-end".equals(entity.getRecommendedRoute())
                        && entity.getSummaryComment().contains("不自动补跑")));
    }

    @Test
    void shouldRaiseP2WhenAutomaticBatchAndCompletedRunLogCountsDiffer() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 2, 2));
        when(paymentTaskCenterMapper.countUnlinkedAutoDayEndBatchesByBizDate(bizDate)).thenReturn(1);
        when(paymentTaskCenterMapper.countUnlinkedCompletedDayEndTaskRunsByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countDuplicateCompletedDayEndTaskRunLinksByBizDate(bizDate)).thenReturn(0);
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getWarningCount());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "P2".equals(entity.getSeverityLevel())
                        && entity.getSummaryComment().contains("自动批次 2 个")
                        && entity.getSummaryComment().contains("缺少成功日志关联的自动批次 1 个")));
    }

    @Test
    void shouldPassP3WhenManualBatchCoversPreviousBusinessDay() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 1, 0));
        when(paymentTaskCenterMapper.countUnlinkedAutoDayEndBatchesByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countUnlinkedCompletedDayEndTaskRunsByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countDuplicateCompletedDayEndTaskRunLinksByBizDate(bizDate)).thenReturn(0);
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getSuccessCount());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "P3".equals(entity.getSeverityLevel())
                        && "SUCCESS".equals(entity.getTaskStatus())
                        && entity.getSummaryComment().contains("人工日终批次")));
    }

    @Test
    void shouldPassP3WhenAutomaticBatchAndCompletedRunLogCountsMatch() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 2, 1));
        when(paymentTaskCenterMapper.countUnlinkedAutoDayEndBatchesByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countUnlinkedCompletedDayEndTaskRunsByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countDuplicateCompletedDayEndTaskRunLinksByBizDate(bizDate)).thenReturn(0);
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertTrue(result.getSummaryComment().contains("一一关联"));
    }

    @Test
    void shouldRaiseP2WhenCompletedRunLogsRepeatTheSameBatchReference() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 1, 1));
        when(paymentTaskCenterMapper.countUnlinkedAutoDayEndBatchesByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countUnlinkedCompletedDayEndTaskRunsByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countDuplicateCompletedDayEndTaskRunLinksByBizDate(bizDate)).thenReturn(1);
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertTrue(result.getSummaryComment().contains("重复关联同一批次的成功日志 1 条"));
    }

    @Test
    void shouldLeaveBusyDayEndPatrolUnrecordedForLaterRetry() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(false, "LEASE_BUSY", bizDate, null, null));
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertTrue(result.getSummaryComment().contains("等待后续重试"));
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .countUnlinkedAutoDayEndBatchesByBizDate(org.mockito.ArgumentMatchers.anyString());
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .countUnlinkedCompletedDayEndTaskRunsByBizDate(org.mockito.ArgumentMatchers.anyString());
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .countDuplicateCompletedDayEndTaskRunLinksByBizDate(org.mockito.ArgumentMatchers.anyString());
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .insertTaskRunLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldTreatDuplicateDayEndPatrolLogAsAlreadyRecorded() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 1, 1));
        when(paymentTaskCenterMapper.countUnlinkedAutoDayEndBatchesByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countUnlinkedCompletedDayEndTaskRunsByBizDate(bizDate)).thenReturn(0);
        when(paymentTaskCenterMapper.countDuplicateCompletedDayEndTaskRunLinksByBizDate(bizDate)).thenReturn(0);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate patrol"))
                .when(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
        stubTaskCenterOverview();

        PaymentTaskActionResultDTO result = buildQueryTaskCenterService().runAutoPaymentDayEndPatrol();

        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertNotNull(result.getOverview());
    }

    @Test
    void shouldFailClosedAndRecordInvalidDayEndPatrolResult() {
        String bizDate = previousShanghaiBusinessDay();
        when(paymentDayEndService.inspectPreviousBusinessDay())
                .thenReturn(dayEndPatrolResult(true, null, bizDate, 1, 2));

        IllegalStateException error = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEndPatrol());

        Assertions.assertEquals("automatic day-end patrol returned an invalid inspected result", error.getMessage());
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_DAY_END_PATROL_FAILED".equals(entity.getSummaryComment())
                        && "P1".equals(entity.getSeverityLevel())
                        && bizDate.equals(entity.getBizDate())));
    }

    @Test
    void shouldRecordSanitizedDayEndPatrolFailureAndRethrow() {
        String bizDate = previousShanghaiBusinessDay();
        IllegalStateException failure = new IllegalStateException("database-password-secret");
        when(paymentDayEndService.inspectPreviousBusinessDay()).thenThrow(failure);

        IllegalStateException thrown = Assertions.assertThrows(
                IllegalStateException.class,
                () -> buildQueryTaskCenterService().runAutoPaymentDayEndPatrol());

        Assertions.assertSame(failure, thrown);
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_DAY_END_PATROL_FAILED".equals(entity.getSummaryComment())
                        && !entity.getSummaryComment().contains("database-password-secret")
                        && entity.getTaskLogNo().startsWith("PTL")
                        && !("PTL-DAY-END-PATROL-" + bizDate.replace("-", ""))
                        .equals(entity.getTaskLogNo())));
    }

    @Test
    void shouldRunAutoRetryFailedRefunds() {
        RefundRetryResultDTO retryResult = new RefundRetryResultDTO();
        retryResult.setProcessedCount(1);
        retryResult.setSuccessCount(1);
        when(refundRetryTaskService.retryFailedRefunds("refund-retry-scheduler")).thenReturn(retryResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(eq("REFUND_FAIL_RETRY"), leaseOwner("refund-retry-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoRetryFailedRefunds();

        verify(refundRetryTaskService).retryFailedRefunds("refund-retry-scheduler");
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "refund-retry-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("REFUND_FAIL_RETRY"), leaseOwner("refund-retry-scheduler"));
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
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100)).thenReturn(Collections.singletonList(candidate));
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100)).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues("payment-core-admin");

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PAYMENT_ISSUE_ESCALATE".equals(entity.getTaskCode())
                        && "P1".equals(entity.getSeverityLevel())
                        && "升级值班负责人".equals(entity.getEscalationStatus())
                        && entity.getSuggestedAction().contains("异常中心")
        ));
        verify(paymentTaskCenterMapper).insertIssueAlertLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PIA".equals(entity.getAlertNo().substring(0, 3))
                        && "ISSUE_ACTIVE:ISSUE-WAIT-PAY-001".equals(entity.getGenerationKey())
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
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100)).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100)).thenReturn(Collections.singletonList(escalationCandidate));
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues("payment-core-admin");

        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        verify(paymentTaskCenterMapper).insertIssueAlertLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "PIA-SOURCE-001".equals(entity.getSourceAlertNo())
                        && "ISSUE_ESCALATION:PIA-SOURCE-001".equals(entity.getGenerationKey())
                        && "支付技术负责人".equals(entity.getReceiver())
                        && "待确认".equals(entity.getAckStatus())
                        && entity.getAlertContent().contains("PIA-SOURCE-001")
                        && entity.getAlertContent().contains("30分钟未确认升级")
        ));
    }

    @Test
    void shouldTruncateEscalationAlertContentBeforeInsert() {
        PaymentIssueAlertCandidateDTO escalationCandidate = new PaymentIssueAlertCandidateDTO();
        escalationCandidate.setIssueNo("ISSUE-WAIT-PAY-002");
        escalationCandidate.setSourceAlertNo("PIA-SOURCE-002");
        escalationCandidate.setPaymentOrderId("PAY-002");
        escalationCandidate.setIssueType("待回调未收口");
        escalationCandidate.setSeverity("P1");
        escalationCandidate.setResponsibilityGroup("支付后端值班组");
        escalationCandidate.setReceiver("支付技术负责人");
        escalationCandidate.setEscalationReceiver("支付技术负责人");
        escalationCandidate.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        escalationCandidate.setEscalationTimeoutMinutes(30);
        escalationCandidate.setScheduleTag("交易链路白班");
        escalationCandidate.setEffectiveWindow("00:00-23:00");
        escalationCandidate.setAlertContent("升级来源告警 PIA-SOURCE-002 已超过 30 分钟未确认，请升级跟进。原告警："
                + repeat("原告警内容过长-", 80));
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(0);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100)).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100)).thenReturn(Collections.singletonList(escalationCandidate));
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class))).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues("payment-core-admin");

        ArgumentCaptor<PaymentIssueAlertLogEntity> captor = ArgumentCaptor.forClass(PaymentIssueAlertLogEntity.class);
        verify(paymentTaskCenterMapper).insertIssueAlertLog(captor.capture());
        Assertions.assertEquals("PIA-SOURCE-002", captor.getValue().getSourceAlertNo());
        Assertions.assertTrue(captor.getValue().getAlertContent().length() <= 512);
        Assertions.assertTrue(captor.getValue().getAlertContent().endsWith("..."));
    }

    @Test
    void shouldRunAutoIssueSlaEscalationTask() {
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(1);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100)).thenReturn(null);
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100)).thenReturn(null);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_ISSUE_ESCALATE"), leaseOwner("payment-issue-sla-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoEscalateOverdueIssues();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode())
                        && "payment-issue-sla-scheduler".equals(entity.getTriggeredBy())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("PAYMENT_ISSUE_ESCALATE"), leaseOwner("payment-issue-sla-scheduler"));
    }

    @Test
    void shouldUseDefaultAndClampIssueSlaCandidateBatchSize() {
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(1))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(1000))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(1))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(1000))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        PaymentTaskCenterServiceImpl service = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        );

        service.runEscalateOverdueIssues("payment-core-admin");
        service.configureIssueSlaCandidateBatchSize(0);
        service.runEscalateOverdueIssues("payment-core-admin");
        service.configureIssueSlaCandidateBatchSize(1001);
        service.runEscalateOverdueIssues("payment-core-admin");

        verify(paymentTaskCenterMapper).findOverdueIssueAlertCandidates(100);
        verify(paymentTaskCenterMapper).findOverdueIssueAlertCandidates(1);
        verify(paymentTaskCenterMapper).findOverdueIssueAlertCandidates(1000);
        verify(paymentTaskCenterMapper).findUnacknowledgedIssueAlertEscalationCandidates(100);
        verify(paymentTaskCenterMapper).findUnacknowledgedIssueAlertEscalationCandidates(1);
        verify(paymentTaskCenterMapper).findUnacknowledgedIssueAlertEscalationCandidates(1000);
    }

    @Test
    void shouldSkipIssueSlaCandidateQueriesWhenAutoLeaseHeldByAnotherInstance() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                eq("PAYMENT_ISSUE_ESCALATE"),
                leaseOwner("payment-issue-sla-scheduler"),
                eq(120)))
                .thenReturn(0);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoEscalateOverdueIssues();

        verify(paymentTaskCenterMapper, org.mockito.Mockito.never()).findOverdueIssueAlertCandidates(
                org.mockito.ArgumentMatchers.anyInt());
        verify(paymentTaskCenterMapper, org.mockito.Mockito.never())
                .findUnacknowledgedIssueAlertEscalationCandidates(
                        org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void shouldRollbackFailedIssueAlertAndContinueEscalationBatch() {
        PaymentIssueAlertCandidateDTO first = buildIssueAlertCandidate("ISSUE-1", "PAY-1");
        PaymentIssueAlertCandidateDTO failed = buildIssueAlertCandidate("ISSUE-FAIL", "PAY-2");
        PaymentIssueAlertCandidateDTO last = buildIssueAlertCandidate("ISSUE-3", "PAY-3");
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(3);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100))
                .thenReturn(Arrays.asList(first, failed, last));
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.insertIssueAlertLog(org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class)))
                .thenAnswer(invocation -> {
                    PaymentIssueAlertLogEntity entity = invocation.getArgument(0);
                    if ("ISSUE-FAIL".equals(entity.getIssueNo())) {
                        throw new IllegalStateException(
                                "jdbc:mysql://internal/payment password=alert-secret\ninvalid SQL");
                    }
                    return 1;
                });
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        transactionAppender.start();
        transactionLogger.addAppender(transactionAppender);
        try {
            PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                    paymentTaskCenterMapper,
                    paymentExpiryTaskService,
                    paymentEventRetryTaskService,
                    refundRetryTaskService,
                    paymentConfigService,
                    paymentIssueAlertDeliveryService,
                    transactionManager
            ).runEscalateOverdueIssues("payment-core-admin");

            Assertions.assertEquals(3, result.getProcessedCount());
            Assertions.assertEquals(2, result.getSuccessCount());
            Assertions.assertEquals(1, result.getFailCount());
            Assertions.assertTrue(result.getSummaryComment().contains("ISSUE-FAIL"));
            verify(transactionManager, org.mockito.Mockito.times(2)).commit(transactionStatus);
            verify(transactionManager).rollback(transactionStatus);
            verify(paymentTaskCenterMapper).insertIssueAlertLog(org.mockito.ArgumentMatchers.argThat(
                    entity -> "ISSUE-3".equals(entity.getIssueNo())));
            Assertions.assertTrue(transactionAppender.list.stream().anyMatch(event ->
                    event.getThrowableProxy() != null
                            && "PAYMENT_ISSUE_ALERT_WRITE_FAILED:IllegalStateException"
                            .equals(event.getThrowableProxy().getMessage())
                            && event.getThrowableProxy().getCause() == null
                            && event.getThrowableProxy().getStackTraceElementProxyArray().length == 0));
            Assertions.assertTrue(transactionAppender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("alert-secret")
                            || (event.getThrowableProxy() != null
                            && String.valueOf(event.getThrowableProxy().getMessage()).contains("alert-secret"))));
        } finally {
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }
    }

    @Test
    void shouldReportConcurrentDuplicateIssueAlertAsSkipped() {
        PaymentIssueAlertCandidateDTO candidate = buildIssueAlertCandidate("ISSUE-DUP", "PAY-DUP");
        when(paymentTaskCenterMapper.countOverduePaymentIssues()).thenReturn(1);
        when(paymentTaskCenterMapper.findOverdueIssueAlertCandidates(100))
                .thenReturn(Collections.singletonList(candidate));
        when(paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates(100))
                .thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.insertIssueAlertLog(
                org.mockito.ArgumentMatchers.any(PaymentIssueAlertLogEntity.class)))
                .thenThrow(new DuplicateKeyException("duplicate generation key"));
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runEscalateOverdueIssues("payment-core-admin");

        Assertions.assertEquals(1, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(1, result.getWarningCount());
        Assertions.assertEquals(0, result.getFailCount());
        Assertions.assertTrue(result.getSummaryComment().contains("并发重复跳过 1 条"));
    }

    @Test
    void shouldSkipAutoCloseExpiredPaymentsWhenLeaseHeldByAnotherInstance() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_EXPIRE_CLOSE"), leaseOwner("payment-expiry-scheduler"), eq(120))).thenReturn(0);

        PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
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
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_CONTROL_SELF_CHECK"), leaseOwner("payment-control-self-check-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoControlPolicySelfChecks();

        verify(paymentConfigService).runAllEnabledControlPolicySelfChecks();
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("PAYMENT_CONTROL_SELF_CHECK"), leaseOwner("payment-control-self-check-scheduler"));
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
                paymentEventRetryTaskService,
                refundRetryTaskService,
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
    void shouldReturnTaskRunDetailWithDerivedFields() {
        PaymentTaskRunLogItemDTO item = new PaymentTaskRunLogItemDTO();
        item.setTaskLogNo("TASK-LOG-001");
        item.setTaskCode("PAYMENT_EVENT_RETRY");
        item.setProcessedCount(2);
        item.setFailCount(1);
        when(paymentTaskCenterMapper.findTaskRunByNo("TASK-LOG-001")).thenReturn(item);

        PaymentTaskRunLogItemDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).taskRunDetail(" TASK-LOG-001 ");

        Assertions.assertEquals("P2", result.getSeverityLevel());
        Assertions.assertEquals("纳入当班跟进", result.getEscalationStatus());
        verify(paymentTaskCenterMapper).findTaskRunByNo("TASK-LOG-001");
    }

    @Test
    void shouldRejectRawOverlongTaskLogNoBeforeTrim() {
        String overlongTaskLogNo = repeat("T", 64) + " ";

        Assertions.assertThrows(IllegalArgumentException.class, () -> new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).taskRunDetail(overlongTaskLogNo));

        verify(paymentTaskCenterMapper, never()).findTaskRunByNo(any());
    }

    @Test
    void shouldEscalateImmediatelyWhenRefundRetryKeepsFailing() {
        RefundRetryResultDTO retryResult = new RefundRetryResultDTO();
        retryResult.setProcessedCount(2);
        retryResult.setFailCount(2);
        retryResult.setFailedRefundOrderIds(Arrays.asList("REF-1", "REF-2"));
        when(refundRetryTaskService.retryFailedRefunds("payment-core-admin")).thenReturn(retryResult);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runRetryFailedRefunds("payment-core-admin");

        ArgumentCaptor<PaymentTaskRunLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentTaskRunLogEntity.class);
        verify(paymentTaskCenterMapper).insertTaskRunLog(logCaptor.capture());
        Assertions.assertEquals("P1", logCaptor.getValue().getSeverityLevel());
        Assertions.assertEquals("升级值班负责人", logCaptor.getValue().getEscalationStatus());
        Assertions.assertTrue(logCaptor.getValue().getSuggestedAction().contains("优先核对退款渠道响应"));
        Assertions.assertTrue(logCaptor.getValue().getSummaryComment().contains("REF-1/REF-2"));
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
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runControlPolicySelfChecks("payment-core-admin");

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
        when(paymentTaskCenterMapper.acquireTaskLease(eq("PAYMENT_CONTROL_SELF_CHECK"), leaseOwner("payment-control-self-check-scheduler"), eq(120))).thenReturn(1);

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoControlPolicySelfChecks();

        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode())
                        && "payment-control-self-check-scheduler".equals(entity.getTriggeredBy())
                        && "/payment-config".equals(entity.getRecommendedRoute())
        ));
        verify(paymentTaskCenterMapper).releaseTaskLease(eq("PAYMENT_CONTROL_SELF_CHECK"), leaseOwner("payment-control-self-check-scheduler"));
    }

    @Test
    void shouldDispatchIssueAlerts() {
        when(paymentIssueAlertDeliveryService.dispatchPendingAlerts("payment-core-admin"))
                .thenReturn(new com.abc123.hsp.dto.PaymentTaskActionResultDTO());
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentTaskActionResultDTO result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runDispatchIssueAlerts("payment-core-admin");

        verify(paymentIssueAlertDeliveryService).dispatchPendingAlerts("payment-core-admin");
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
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService
        ).runAutoDispatchIssueAlerts();

        verify(paymentIssueAlertDeliveryService).autoDispatchPendingAlerts();
        Assertions.assertNotNull(result.getOverview());
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private PaymentIssueAlertCandidateDTO buildIssueAlertCandidate(String issueNo, String paymentOrderId) {
        PaymentIssueAlertCandidateDTO candidate = new PaymentIssueAlertCandidateDTO();
        candidate.setIssueNo(issueNo);
        candidate.setPaymentOrderId(paymentOrderId);
        candidate.setIssueType("待回调未收口");
        candidate.setSeverity("P1");
        candidate.setResponsibilityGroup("支付后端值班组");
        candidate.setReceiver("支付后端值班");
        candidate.setAlertContent("支付异常 " + issueNo + " 已超过 P1 SLA，请进入异常中心处理。");
        return candidate;
    }

    private PaymentTaskCenterServiceImpl buildQueryTaskCenterService() {
        return buildQueryTaskCenterService(20, 100);
    }

    private PaymentTaskCenterServiceImpl buildQueryTaskCenterService(
            int pendingQueryPerChannelBatchSize,
            int pendingQueryBatchSize) {
        PaymentTaskCenterServiceImpl service = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService,
                null,
                paymentService,
                30,
                60,
                2,
                900,
                pendingQueryPerChannelBatchSize,
                pendingQueryBatchSize,
                24,
                5,
                30,
                10,
                10,
                60,
                10,
                15,
                10,
                30,
                60,
                10,
                30);
        service.setRefundQueryTaskService(refundQueryTaskService);
        service.setPaymentDayEndService(paymentDayEndService);
        return service;
    }

    private PaymentDayEndAutoRunResultDTO dayEndResult(
            boolean executed, String skipReason, String batchStatus) {
        PaymentDayEndAutoRunResultDTO result = new PaymentDayEndAutoRunResultDTO();
        result.setExecuted(executed);
        result.setSkipReason(skipReason);
        result.setBizDate("2026-09-11");
        result.setBatchNo(executed ? "DEB-20260911-AUTO-001" : null);
        result.setBatchStatus(batchStatus);
        return result;
    }

    private PaymentDayEndPatrolResultDTO dayEndPatrolResult(
            boolean inspected, String skipReason, String bizDate, Integer batchCount, Integer autoBatchCount) {
        PaymentDayEndPatrolResultDTO result = new PaymentDayEndPatrolResultDTO();
        result.setInspected(inspected);
        result.setSkipReason(skipReason);
        result.setBizDate(bizDate);
        result.setBatchCount(batchCount);
        result.setAutoBatchCount(autoBatchCount);
        return result;
    }

    private String previousShanghaiBusinessDay() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
    }

    private void stubTaskCenterOverview() {
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());
    }

    private String leaseOwner(String prefix) {
        return org.mockito.ArgumentMatchers.argThat(
                owner -> owner != null && owner.startsWith(prefix + ":"));
    }
}
