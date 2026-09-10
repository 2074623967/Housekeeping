package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.dto.PaymentEventRetryResultDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentQueryTrendPointDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.dto.RefundRetryResultDTO;
import com.abc123.hsp.dto.RefundQueryTaskResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import com.abc123.hsp.service.PaymentConfigService;
import com.abc123.hsp.service.PaymentEventRetryTaskService;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import com.abc123.hsp.service.PaymentTaskLeaseService;
import com.abc123.hsp.service.RefundRetryTaskService;
import com.abc123.hsp.service.RefundQueryTaskService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付任务中心服务默认实现。
 */
@Service
public class PaymentTaskCenterServiceImpl implements PaymentTaskCenterService {

    private static final String RUN_MODE_MANUAL = "MANUAL";
    private static final String RUN_MODE_AUTO = "AUTO";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_STATUS_WARN = "WARNING";
    private static final String TASK_CODE_EXPIRE_CLOSE = "PAYMENT_EXPIRE_CLOSE";
    private static final String TASK_CODE_PENDING_QUERY = "PAYMENT_PENDING_QUERY";
    private static final String TASK_CODE_REFUND_PENDING_QUERY = "REFUND_PENDING_QUERY";
    private static final String TASK_CODE_QUERY_MONITOR_ALERT = "PAYMENT_QUERY_MONITOR_ALERT";
    private static final String TASK_CODE_EVENT_RETRY = "PAYMENT_EVENT_RETRY";
    private static final String TASK_CODE_REFUND_RETRY = "REFUND_FAIL_RETRY";
    private static final String TASK_CODE_ISSUE_ESCALATE = "PAYMENT_ISSUE_ESCALATE";
    private static final String TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE = "PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE";
    private static final String TASK_CODE_CONTROL_SELF_CHECK = "PAYMENT_CONTROL_SELF_CHECK";
    private static final int AUTO_TASK_LEASE_SECONDS = 120;
    private static final int ISSUE_ALERT_CONTENT_MAX_LENGTH = 512;
    private static final String ISSUE_ACTIVE_GENERATION_KEY_PREFIX = "ISSUE_ACTIVE:";
    private static final String ISSUE_ESCALATION_GENERATION_KEY_PREFIX = "ISSUE_ESCALATION:";
    private static final String QUERY_FAILURE_ISSUE_TYPE = "主动查单失败趋势";
    private static final String QUERY_RATE_LIMIT_ISSUE_TYPE = "主动查单限流拒绝";

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private PaymentTaskLeaseService paymentTaskLeaseService;
    private final PaymentExpiryTaskService paymentExpiryTaskService;
    private final PaymentEventRetryTaskService paymentEventRetryTaskService;
    private final RefundRetryTaskService refundRetryTaskService;
    private RefundQueryTaskService refundQueryTaskService;
    private final PaymentConfigService paymentConfigService;
    private final PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService;
    private final String taskLeaseOwnerSuffix = UUID.randomUUID().toString().replace("-", "");
    private final TransactionTemplate issueAlertTransactionTemplate;
    private final PaymentService paymentService;
    private final int pendingQueryMinAgeSeconds;
    private final int pendingQueryRetryIntervalSeconds;
    private final int pendingQueryRetryBackoffFactor;
    private final int pendingQueryMaxRetryIntervalSeconds;
    private final int pendingQueryPerChannelBatchSize;
    private final int pendingQueryBatchSize;
    private final int queryMonitorLookbackHours;
    private final int queryFailureCountThreshold;
    private final int queryFailureRateThresholdPercent;
    private final int queryFailureRateMinimumSampleCount;
    private final int queryRateLimitRejectionCountThreshold;
    private final int queryMonitorAlertCooldownMinutes;
    private final int issueAlertDispatchCountThreshold;
    private final int issueAlertDispatchTimeoutMinutes;
    private final int issueAlertReceiptCountThreshold;
    private final int issueAlertReceiptTimeoutMinutes;
    private final int issueAlertReceiptQueryMinAgeSeconds;
    private final int issueAlertReceiptQueryMaxAttempts;
    private final int issueAlertReceiptQueryClaimTimeoutSeconds;

    @Autowired
    public PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                        PaymentExpiryTaskService paymentExpiryTaskService,
                                        PaymentEventRetryTaskService paymentEventRetryTaskService,
                                        RefundRetryTaskService refundRetryTaskService,
                                        PaymentConfigService paymentConfigService,
                                        PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService,
                                        PlatformTransactionManager transactionManager,
                                        PaymentService paymentService,
                                        @Value("${payment.channel-query.compensation.min-age-seconds:30}")
                                                int pendingQueryMinAgeSeconds,
                                        @Value("${payment.channel-query.compensation.retry-interval-seconds:60}")
                                                int pendingQueryRetryIntervalSeconds,
                                        @Value("${payment.channel-query.compensation.retry-backoff-factor:2}")
                                                int pendingQueryRetryBackoffFactor,
                                        @Value("${payment.channel-query.compensation.max-retry-interval-seconds:900}")
                                                int pendingQueryMaxRetryIntervalSeconds,
                                        @Value("${payment.channel-query.compensation.per-channel-batch-size:20}")
                                                int pendingQueryPerChannelBatchSize,
                                        @Value("${payment.channel-query.compensation.batch-size:100}")
                                                int pendingQueryBatchSize,
                                        @Value("${payment.channel-query.monitor.lookback-hours:24}")
                                                int queryMonitorLookbackHours,
                                        @Value("${payment.channel-query.monitor.failure-count-threshold:5}")
                                                int queryFailureCountThreshold,
                                        @Value("${payment.channel-query.monitor.failure-rate-threshold-percent:30}")
                                                int queryFailureRateThresholdPercent,
                                        @Value("${payment.channel-query.monitor.failure-rate-minimum-sample-count:10}")
                                                int queryFailureRateMinimumSampleCount,
                                        @Value("${payment.channel-query.monitor.rate-limit-rejection-count-threshold:10}")
                                                int queryRateLimitRejectionCountThreshold,
                                        @Value("${payment.channel-query.monitor.alert-cooldown-minutes:60}")
                                                int queryMonitorAlertCooldownMinutes,
                                        @Value("${payment.issue-alert.monitor.dispatch-count-threshold:10}")
                                                int issueAlertDispatchCountThreshold,
                                        @Value("${payment.issue-alert.monitor.dispatch-timeout-minutes:15}")
                                                int issueAlertDispatchTimeoutMinutes,
                                        @Value("${payment.issue-alert.monitor.receipt-count-threshold:10}")
                                                int issueAlertReceiptCountThreshold,
                                        @Value("${payment.issue-alert.monitor.receipt-timeout-minutes:30}")
                                                int issueAlertReceiptTimeoutMinutes,
                                        @Value(
                                                "${payment.issue-alert.receipt-query.compensation.min-age-seconds:60}")
                                                int issueAlertReceiptQueryMinAgeSeconds,
                                        @Value(
                                                "${payment.issue-alert.receipt-query.compensation.max-attempts:10}")
                                                int issueAlertReceiptQueryMaxAttempts,
                                        @Value(
                                                "${payment.issue-alert.receipt-query.compensation.claim-timeout-seconds:30}")
                                                int issueAlertReceiptQueryClaimTimeoutSeconds) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.paymentTaskLeaseService = new PaymentTaskLeaseService(paymentTaskCenterMapper);
        this.paymentExpiryTaskService = paymentExpiryTaskService;
        this.paymentEventRetryTaskService = paymentEventRetryTaskService;
        this.refundRetryTaskService = refundRetryTaskService;
        this.paymentConfigService = paymentConfigService;
        this.paymentIssueAlertDeliveryService = paymentIssueAlertDeliveryService;
        this.issueAlertTransactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
        if (this.issueAlertTransactionTemplate != null) {
            this.issueAlertTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        this.paymentService = paymentService;
        this.pendingQueryMinAgeSeconds = Math.max(pendingQueryMinAgeSeconds, 0);
        this.pendingQueryRetryIntervalSeconds = Math.max(pendingQueryRetryIntervalSeconds, 1);
        this.pendingQueryRetryBackoffFactor = Math.max(pendingQueryRetryBackoffFactor, 1);
        this.pendingQueryMaxRetryIntervalSeconds = Math.max(
                pendingQueryMaxRetryIntervalSeconds,
                this.pendingQueryRetryIntervalSeconds);
        this.pendingQueryPerChannelBatchSize = Math.max(pendingQueryPerChannelBatchSize, 1);
        this.pendingQueryBatchSize = Math.max(pendingQueryBatchSize, 1);
        this.queryMonitorLookbackHours = Math.min(Math.max(queryMonitorLookbackHours, 1), 168);
        this.queryFailureCountThreshold = Math.max(queryFailureCountThreshold, 1);
        this.queryFailureRateThresholdPercent = Math.min(
                Math.max(queryFailureRateThresholdPercent, 1), 100);
        this.queryFailureRateMinimumSampleCount = Math.max(queryFailureRateMinimumSampleCount, 1);
        this.queryRateLimitRejectionCountThreshold = Math.max(queryRateLimitRejectionCountThreshold, 1);
        this.queryMonitorAlertCooldownMinutes = Math.max(queryMonitorAlertCooldownMinutes, 1);
        this.issueAlertDispatchCountThreshold = Math.max(issueAlertDispatchCountThreshold, 1);
        this.issueAlertDispatchTimeoutMinutes = Math.max(issueAlertDispatchTimeoutMinutes, 1);
        this.issueAlertReceiptCountThreshold = Math.max(issueAlertReceiptCountThreshold, 1);
        this.issueAlertReceiptTimeoutMinutes = Math.max(issueAlertReceiptTimeoutMinutes, 1);
        this.issueAlertReceiptQueryMinAgeSeconds = Math.max(issueAlertReceiptQueryMinAgeSeconds, 1);
        this.issueAlertReceiptQueryMaxAttempts = Math.max(issueAlertReceiptQueryMaxAttempts, 1);
        this.issueAlertReceiptQueryClaimTimeoutSeconds = Math.max(
                issueAlertReceiptQueryClaimTimeoutSeconds, 1);
    }

    @Autowired
    void setPaymentTaskLeaseService(PaymentTaskLeaseService paymentTaskLeaseService) {
        this.paymentTaskLeaseService = paymentTaskLeaseService;
    }

    @Autowired
    void setRefundQueryTaskService(RefundQueryTaskService refundQueryTaskService) {
        this.refundQueryTaskService = refundQueryTaskService;
    }

    PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                 PaymentExpiryTaskService paymentExpiryTaskService,
                                 PaymentEventRetryTaskService paymentEventRetryTaskService,
                                 RefundRetryTaskService refundRetryTaskService,
                                 PaymentConfigService paymentConfigService,
                                 PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService) {
        this(paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService,
                null,
                null,
                30,
                60,
                2,
                900,
                20,
                100,
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
    }

    PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                 PaymentExpiryTaskService paymentExpiryTaskService,
                                 PaymentEventRetryTaskService paymentEventRetryTaskService,
                                 RefundRetryTaskService refundRetryTaskService,
                                 PaymentConfigService paymentConfigService,
                                 PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService,
                                 PlatformTransactionManager transactionManager) {
        this(paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventRetryTaskService,
                refundRetryTaskService,
                paymentConfigService,
                paymentIssueAlertDeliveryService,
                transactionManager,
                null,
                30,
                60,
                2,
                900,
                20,
                100,
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
    }

    @Override
    public PaymentTaskCenterOverviewDTO overview() {
        PaymentTaskCenterOverviewDTO overview = paymentTaskCenterMapper.findOverviewSummary();
        overview.setUnknownRefundSubmitEventCount(
                paymentTaskCenterMapper.countUnknownRefundSubmitEvents());
        overview.setPendingQueryBacklogCount(paymentTaskCenterMapper.countPendingPaymentQueryBacklog(
                pendingQueryMinAgeSeconds));
        overview.setDuePendingQueryCount(paymentTaskCenterMapper.countDuePendingPaymentQueries(
                pendingQueryMinAgeSeconds,
                pendingQueryRetryIntervalSeconds,
                pendingQueryRetryBackoffFactor,
                pendingQueryMaxRetryIntervalSeconds));
        overview.setPendingRefundQueryBacklogCount(
                refundQueryTaskService == null ? 0 : refundQueryTaskService.countPendingRefunds());
        overview.setDuePendingRefundQueryCount(
                refundQueryTaskService == null ? 0 : refundQueryTaskService.countDueRefunds());
        int recentQueryCount = paymentTaskCenterMapper.countRecentChannelQueries(queryMonitorLookbackHours);
        int recentQueryFailureCount = paymentTaskCenterMapper.countRecentChannelQueryFailures(
                queryMonitorLookbackHours);
        int recentQueryRateLimitRejectionCount =
                paymentTaskCenterMapper.countRecentChannelQueryRateLimitRejections(queryMonitorLookbackHours);
        List<PaymentQueryTrendPointDTO> queryTrends = paymentTaskCenterMapper.findRecentChannelQueryTrends(
                queryMonitorLookbackHours);
        for (PaymentQueryTrendPointDTO trend : queryTrends) {
            trend.setFailureRate(formatPercentage(
                    valueOrZero(trend.getFailureCount()),
                    valueOrZero(trend.getQueryCount())));
        }
        overview.setQueryMonitorWindowHours(queryMonitorLookbackHours);
        overview.setRecentQueryCount(recentQueryCount);
        overview.setRecentQueryFailureCount(recentQueryFailureCount);
        overview.setRecentQueryFailureRate(formatPercentage(recentQueryFailureCount, recentQueryCount));
        overview.setQueryFailureCountThreshold(queryFailureCountThreshold);
        overview.setQueryFailureRateThresholdPercent(queryFailureRateThresholdPercent);
        overview.setQueryFailureRateMinimumSampleCount(queryFailureRateMinimumSampleCount);
        overview.setRecentQueryRateLimitRejectionCount(recentQueryRateLimitRejectionCount);
        overview.setQueryRateLimitRejectionCountThreshold(queryRateLimitRejectionCountThreshold);
        overview.setPendingIssueAlertDispatchCount(paymentTaskCenterMapper.countPendingIssueAlertDispatches());
        overview.setFailedIssueAlertDispatchCount(paymentTaskCenterMapper.countFailedIssueAlertDispatches());
        overview.setOldestPendingIssueAlertDispatchMinutes(
                paymentTaskCenterMapper.findOldestPendingIssueAlertDispatchMinutes());
        overview.setIssueAlertDispatchCountThreshold(issueAlertDispatchCountThreshold);
        overview.setIssueAlertDispatchTimeoutMinutes(issueAlertDispatchTimeoutMinutes);
        overview.setPendingIssueAlertReceiptCount(paymentTaskCenterMapper.countPendingIssueAlertReceipts());
        overview.setDueIssueAlertReceiptCount(paymentTaskCenterMapper.countDueIssueAlertReceipts(
                issueAlertReceiptQueryMinAgeSeconds,
                issueAlertReceiptQueryMaxAttempts,
                issueAlertReceiptQueryClaimTimeoutSeconds));
        overview.setBackingOffIssueAlertReceiptCount(paymentTaskCenterMapper.countBackingOffIssueAlertReceipts(
                issueAlertReceiptQueryMaxAttempts,
                issueAlertReceiptQueryClaimTimeoutSeconds));
        overview.setQueryingIssueAlertReceiptCount(paymentTaskCenterMapper.countQueryingIssueAlertReceipts(
                issueAlertReceiptQueryClaimTimeoutSeconds));
        overview.setExhaustedIssueAlertReceiptCount(paymentTaskCenterMapper.countExhaustedIssueAlertReceipts(
                issueAlertReceiptQueryMaxAttempts,
                issueAlertReceiptQueryClaimTimeoutSeconds));
        overview.setOldestPendingIssueAlertReceiptMinutes(
                paymentTaskCenterMapper.findOldestPendingIssueAlertReceiptMinutes());
        overview.setIssueAlertReceiptCountThreshold(issueAlertReceiptCountThreshold);
        overview.setIssueAlertReceiptTimeoutMinutes(issueAlertReceiptTimeoutMinutes);
        overview.setQueryTrends(queryTrends);
        overview.setFocusAlerts(buildFocusAlerts(overview));
        overview.setRecentTaskRuns(enrichTaskRuns(paymentTaskCenterMapper.findRecentTaskRuns()));
        return overview;
    }

    @Override
    public PageResultDTO<PaymentTaskRunLogItemDTO> listTaskRuns(PaymentTaskRunLogQueryDTO query) {
        PaymentTaskRunLogQueryDTO normalizedQuery = normalizeQuery(query);
        long total = paymentTaskCenterMapper.countTaskRunLogs(normalizedQuery);
        List<PaymentTaskRunLogItemDTO> items = enrichTaskRuns(paymentTaskCenterMapper.findTaskRunLogs(normalizedQuery));
        return new PageResultDTO<>(items, total, normalizedQuery.getPageNo(), normalizedQuery.getPageSize());
    }

    @Override
    public String exportTaskRunsCsv(PaymentTaskRunLogQueryDTO query) {
        PaymentTaskRunLogQueryDTO normalizedQuery = normalizeQuery(query);
        List<PaymentTaskRunLogItemDTO> items =
                enrichTaskRuns(paymentTaskCenterMapper.findTaskRunLogsForExport(normalizedQuery));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("日志号,任务编码,任务名称,运行方式,任务状态,严重等级,升级状态,处理总数,成功数,告警数,失败数,执行摘要,建议动作,推荐路由,触发人,开始时间,完成时间\n");
        for (PaymentTaskRunLogItemDTO item : items) {
            builder.append(csvCell(item.getTaskLogNo())).append(',')
                    .append(csvCell(item.getTaskCode())).append(',')
                    .append(csvCell(item.getTaskName())).append(',')
                    .append(csvCell(item.getRunMode())).append(',')
                    .append(csvCell(item.getTaskStatus())).append(',')
                    .append(csvCell(item.getSeverityLevel())).append(',')
                    .append(csvCell(item.getEscalationStatus())).append(',')
                    .append(csvCell(valueOf(item.getProcessedCount()))).append(',')
                    .append(csvCell(valueOf(item.getSuccessCount()))).append(',')
                    .append(csvCell(valueOf(item.getWarningCount()))).append(',')
                    .append(csvCell(valueOf(item.getFailCount()))).append(',')
                    .append(csvCell(item.getSummaryComment())).append(',')
                    .append(csvCell(item.getSuggestedAction())).append(',')
                    .append(csvCell(item.getRecommendedRoute())).append(',')
                    .append(csvCell(item.getTriggeredBy())).append(',')
                    .append(csvCell(item.getStartedAt())).append(',')
                    .append(csvCell(item.getCompletedAt())).append('\n');
        }
        return builder.toString();
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runCloseExpiredPayments() {
        return runCloseExpiredPaymentsByMode(RUN_MODE_MANUAL, "payment-core-admin", "人工触发");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoCloseExpiredPayments() {
        return runAutoTaskWithLease(
                TASK_CODE_EXPIRE_CLOSE,
                "支付超时关单",
                "payment-expiry-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runCloseExpiredPaymentsByMode(RUN_MODE_AUTO, "payment-expiry-scheduler", "自动调度");
                    }
                }
        );
    }

    @Override
    public PaymentTaskActionResultDTO runPendingPaymentQueries() {
        return runPendingPaymentQueriesByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    public PaymentTaskActionResultDTO runAutoPendingPaymentQueries() {
        return runAutoTaskWithLease(
                TASK_CODE_PENDING_QUERY,
                "待回调支付主动查单",
                "payment-query-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runPendingPaymentQueriesByMode(RUN_MODE_AUTO, "payment-query-scheduler");
                    }
                }
        );
    }

    @Override
    public PaymentTaskActionResultDTO runPendingRefundQueries() {
        return runPendingRefundQueriesByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    public PaymentTaskActionResultDTO runAutoPendingRefundQueries() {
        return runAutoTaskWithLease(
                TASK_CODE_REFUND_PENDING_QUERY,
                "处理中退款主动查单",
                "refund-query-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runPendingRefundQueriesByMode(RUN_MODE_AUTO, "refund-query-scheduler");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runGenerateQueryMonitorAlerts() {
        return runTaskWithLease(
                TASK_CODE_QUERY_MONITOR_ALERT,
                "主动查单监控告警",
                RUN_MODE_MANUAL,
                "payment-core-admin",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runGenerateQueryMonitorAlertsByMode(RUN_MODE_MANUAL, "payment-core-admin");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoGenerateQueryMonitorAlerts() {
        return runTaskWithLease(
                TASK_CODE_QUERY_MONITOR_ALERT,
                "主动查单监控告警",
                RUN_MODE_AUTO,
                "payment-query-monitor-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runGenerateQueryMonitorAlertsByMode(
                                RUN_MODE_AUTO,
                                "payment-query-monitor-scheduler");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRepublishFailedEvents() {
        return runRepublishFailedEventsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoRepublishFailedEvents() {
        return runAutoTaskWithLease(
                TASK_CODE_EVENT_RETRY,
                "失败事件重发",
                "payment-event-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runRepublishFailedEventsByMode(RUN_MODE_AUTO, "payment-event-scheduler");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRetryFailedRefunds() {
        return runRetryFailedRefundsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoRetryFailedRefunds() {
        return runAutoTaskWithLease(
                TASK_CODE_REFUND_RETRY,
                "失败退款重试",
                "refund-retry-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runRetryFailedRefundsByMode(RUN_MODE_AUTO, "refund-retry-scheduler");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runEscalateOverdueIssues() {
        return runEscalateOverdueIssuesByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runDispatchIssueAlerts() {
        PaymentTaskActionResultDTO result = paymentIssueAlertDeliveryService.dispatchPendingAlerts();
        result.setOverview(overview());
        return result;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoEscalateOverdueIssues() {
        return runAutoTaskWithLease(
                TASK_CODE_ISSUE_ESCALATE,
                "异常 SLA 升级巡检",
                "payment-issue-sla-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runEscalateOverdueIssuesByMode(RUN_MODE_AUTO, "payment-issue-sla-scheduler");
                    }
                }
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoDispatchIssueAlerts() {
        PaymentTaskActionResultDTO result = paymentIssueAlertDeliveryService.autoDispatchPendingAlerts();
        result.setOverview(overview());
        return result;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runReconcileIssueAlertReceipts() {
        PaymentTaskActionResultDTO result = paymentIssueAlertDeliveryService.reconcileDeliveryReceipts();
        result.setOverview(overview());
        return result;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoReconcileIssueAlertReceipts() {
        PaymentTaskActionResultDTO result = paymentIssueAlertDeliveryService.autoReconcileDeliveryReceipts();
        result.setOverview(overview());
        return result;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runControlPolicySelfChecks() {
        return runControlPolicySelfChecksByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoControlPolicySelfChecks() {
        return runAutoTaskWithLease(
                TASK_CODE_CONTROL_SELF_CHECK,
                "支付控制策略自动巡检",
                "payment-control-self-check-scheduler",
                new AutoTaskRunner() {
                    @Override
                    public PaymentTaskActionResultDTO run() {
                        return runControlPolicySelfChecksByMode(RUN_MODE_AUTO, "payment-control-self-check-scheduler");
                    }
                }
        );
    }

    private PaymentTaskActionResultDTO runAutoTaskWithLease(String taskCode,
                                                            String taskName,
                                                            String triggeredBy,
                                                            AutoTaskRunner runner) {
        return runTaskWithLease(taskCode, taskName, RUN_MODE_AUTO, triggeredBy, runner);
    }

    private PaymentTaskActionResultDTO runTaskWithLease(String taskCode,
                                                        String taskName,
                                                        String runMode,
                                                        String triggeredBy,
                                                        AutoTaskRunner runner) {
        String leaseOwner = triggeredBy + ":" + taskLeaseOwnerSuffix;
        PaymentTaskLeaseService.Lease lease = paymentTaskLeaseService.tryAcquire(
                taskCode, leaseOwner, AUTO_TASK_LEASE_SECONDS);
        if (lease == null) {
            return buildLeaseSkippedResult(taskCode, taskName, runMode, triggeredBy);
        }
        try {
            return runner.run();
        } finally {
            lease.close();
        }
    }

    private PaymentTaskActionResultDTO runGenerateQueryMonitorAlertsByMode(String runMode, String triggeredBy) {
        int queryCount = paymentTaskCenterMapper.countRecentChannelQueries(queryMonitorLookbackHours);
        int failureCount = paymentTaskCenterMapper.countRecentChannelQueryFailures(queryMonitorLookbackHours);
        int rejectionCount = paymentTaskCenterMapper.countRecentChannelQueryRateLimitRejections(
                queryMonitorLookbackHours);
        boolean failureThresholdReached = failureCount >= queryFailureCountThreshold
                || (queryCount >= queryFailureRateMinimumSampleCount
                && failureCount * 100L >= queryCount * (long) queryFailureRateThresholdPercent);
        boolean rejectionThresholdReached = rejectionCount >= queryRateLimitRejectionCountThreshold;

        int matchedCount = 0;
        int generatedCount = 0;
        if (failureThresholdReached) {
            matchedCount++;
            generatedCount += generateQueryMonitorAlertIfAllowed(
                    QUERY_FAILURE_ISSUE_TYPE,
                    "QUERY-MONITOR-FAILURE",
                    String.format(
                            "近 %d 小时支付与退款主动查单 %d 次，查询失败 %d 次，失败率 %s；告警阈值为失败 %d 次，或样本不少于 %d 次且失败率达到 %d%%。",
                            queryMonitorLookbackHours,
                            queryCount,
                            failureCount,
                            formatPercentage(failureCount, queryCount),
                            queryFailureCountThreshold,
                            queryFailureRateMinimumSampleCount,
                            queryFailureRateThresholdPercent),
                    triggeredBy);
        }
        if (rejectionThresholdReached) {
            matchedCount++;
            generatedCount += generateQueryMonitorAlertIfAllowed(
                    QUERY_RATE_LIMIT_ISSUE_TYPE,
                    "QUERY-MONITOR-RATE-LIMIT",
                    String.format(
                            "近 %d 小时支付与退款主动查单限流拒绝 %d 次，已达到 %d 次告警阈值；拒绝事实独立统计，不计入渠道查询失败率。",
                            queryMonitorLookbackHours,
                            rejectionCount,
                            queryRateLimitRejectionCountThreshold),
                    triggeredBy);
        }
        int suppressedCount = matchedCount - generatedCount;
        return buildAndRecordResult(
                TASK_CODE_QUERY_MONITOR_ALERT,
                "主动查单监控告警",
                runMode,
                triggeredBy,
                matchedCount,
                generatedCount,
                0,
                0,
                matchedCount == 0
                        ? "支付与退款主动查单失败和限流拒绝指标均未达到告警阈值。"
                        : String.format("命中 %d 类主动查单监控阈值，生成 %d 条 outbox 告警，冷却窗口抑制 %d 条重复告警。",
                        matchedCount, generatedCount, suppressedCount)
        );
    }

    private int generateQueryMonitorAlertIfAllowed(String issueType,
                                                   String issueNo,
                                                   String alertContent,
                                                   String triggeredBy) {
        if (paymentTaskCenterMapper.countRecentUnacknowledgedQueryMonitorAlerts(
                issueType, queryMonitorAlertCooldownMinutes) > 0) {
            return 0;
        }
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo(buildAlertLogNo());
        entity.setIssueNo(issueNo);
        entity.setPaymentOrderId("QUERY-MONITOR");
        entity.setIssueType(issueType);
        entity.setSeverity("P1");
        entity.setResponsibilityGroup("支付渠道运营组");
        entity.setAlertChannel("IN_APP_OUTBOX");
        entity.setReceiver("支付运营");
        entity.setAlertStatus("已生成");
        entity.setAlertStatusType("warn");
        entity.setAckStatus("待确认");
        entity.setAckStatusType("warn");
        entity.setAlertContent(truncateIssueAlertContent(alertContent));
        entity.setTriggeredBy(triggeredBy);
        return paymentTaskCenterMapper.insertIssueAlertLog(entity);
    }

    private PaymentTaskActionResultDTO runControlPolicySelfChecksByMode(String runMode, String triggeredBy) {
        PaymentControlPolicySelfCheckSummaryDTO summary = paymentConfigService.runAllEnabledControlPolicySelfChecks();
        int processedCount = valueOrZero(summary.getProcessedCount());
        int successCount = valueOrZero(summary.getPassCount());
        int warnCount = valueOrZero(summary.getWarnCount());
        int failCount = valueOrZero(summary.getFailCount());
        return buildAndRecordResult(
                TASK_CODE_CONTROL_SELF_CHECK,
                "支付控制策略自动巡检",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
                warnCount,
                failCount,
                processedCount == 0
                        ? "当前没有启用中的支付控制策略需要巡检。"
                        : String.format("已巡检 %d 条支付控制策略，通过 %d 条，告警 %d 条，失败 %d 条。", processedCount, successCount, warnCount, failCount)
        );
    }

    private PaymentTaskActionResultDTO runEscalateOverdueIssuesByMode(String runMode, String triggeredBy) {
        int overdueIssueCount = paymentTaskCenterMapper.countOverduePaymentIssues();
        List<PaymentIssueAlertCandidateDTO> alertCandidates = paymentTaskCenterMapper.findOverdueIssueAlertCandidates();
        List<PaymentIssueAlertCandidateDTO> escalationCandidates = paymentTaskCenterMapper.findUnacknowledgedIssueAlertEscalationCandidates();
        int generatedAlertCount = 0;
        int duplicateAlertCount = 0;
        int failCount = 0;
        List<String> failedIssueNos = new ArrayList<String>();
        for (PaymentIssueAlertCandidateDTO candidate : alertCandidates) {
            IssueAlertGenerationResult generationResult = generateIssueAlert(candidate, triggeredBy, failedIssueNos);
            if (generationResult == IssueAlertGenerationResult.GENERATED) {
                generatedAlertCount++;
            } else if (generationResult == IssueAlertGenerationResult.DUPLICATE) {
                duplicateAlertCount++;
            } else {
                failCount++;
            }
        }
        for (PaymentIssueAlertCandidateDTO candidate : escalationCandidates) {
            IssueAlertGenerationResult generationResult = generateIssueAlert(candidate, triggeredBy, failedIssueNos);
            if (generationResult == IssueAlertGenerationResult.GENERATED) {
                generatedAlertCount++;
            } else if (generationResult == IssueAlertGenerationResult.DUPLICATE) {
                duplicateAlertCount++;
            } else {
                failCount++;
            }
        }
        int processedCount = overdueIssueCount + escalationCandidates.size();
        String failedIssueHint = failedIssueNos.isEmpty()
                ? ""
                : "，失败异常：" + String.join("/", failedIssueNos);
        return buildAndRecordResult(
                TASK_CODE_ISSUE_ESCALATE,
                "异常 SLA 升级巡检",
                runMode,
                triggeredBy,
                processedCount,
                generatedAlertCount,
                duplicateAlertCount,
                failCount,
                processedCount == 0
                        ? "当前没有超过 SLA 的支付交易异常。"
                        : String.format("发现 %d 条超过 SLA 的支付交易异常，%d 条未确认告警触发升级，本次生成 %d 条告警通知，并发重复跳过 %d 条，失败 %d 条%s。",
                        overdueIssueCount, escalationCandidates.size(), generatedAlertCount, duplicateAlertCount,
                        failCount, failedIssueHint)
        );
    }

    private IssueAlertGenerationResult generateIssueAlert(PaymentIssueAlertCandidateDTO candidate,
                                                          String triggeredBy,
                                                          List<String> failedIssueNos) {
        try {
            Integer insertedRows = executeIssueAlertTransaction(() ->
                    paymentTaskCenterMapper.insertIssueAlertLog(buildIssueAlertLog(candidate, triggeredBy)));
            if (valueOrZero(insertedRows) > 0) {
                return IssueAlertGenerationResult.GENERATED;
            }
        } catch (DuplicateKeyException exception) {
            return IssueAlertGenerationResult.DUPLICATE;
        } catch (RuntimeException exception) {
            // 当前候选由独立事务回滚，批次继续处理后续异常。
        }
        recordFailedIssueNo(failedIssueNos, candidate);
        return IssueAlertGenerationResult.FAILED;
    }

    private <T> T executeIssueAlertTransaction(Supplier<T> action) {
        if (issueAlertTransactionTemplate == null) {
            return action.get();
        }
        return issueAlertTransactionTemplate.execute(status -> action.get());
    }

    private void recordFailedIssueNo(List<String> failedIssueNos, PaymentIssueAlertCandidateDTO candidate) {
        if (failedIssueNos.size() >= 3 || candidate == null) {
            return;
        }
        String referenceNo = hasText(candidate.getSourceAlertNo())
                ? candidate.getSourceAlertNo().trim()
                : candidate.getIssueNo();
        if (hasText(referenceNo)) {
            failedIssueNos.add(referenceNo.trim());
        }
    }

    private PaymentIssueAlertLogEntity buildIssueAlertLog(PaymentIssueAlertCandidateDTO candidate, String triggeredBy) {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo(buildAlertLogNo());
        entity.setSourceAlertNo(candidate.getSourceAlertNo());
        entity.setGenerationKey(buildIssueAlertGenerationKey(candidate));
        entity.setIssueNo(candidate.getIssueNo());
        entity.setPaymentOrderId(candidate.getPaymentOrderId());
        entity.setIssueType(candidate.getIssueType());
        entity.setSeverity(candidate.getSeverity());
        entity.setResponsibilityGroup(candidate.getResponsibilityGroup());
        entity.setAlertChannel("IN_APP_OUTBOX");
        entity.setReceiver(candidate.getReceiver());
        entity.setAlertStatus("已生成");
        entity.setAlertStatusType("warn");
        entity.setAckStatus("待确认");
        entity.setAckStatusType("warn");
        entity.setAlertContent(truncateIssueAlertContent(buildScheduledAlertContent(candidate)));
        entity.setTriggeredBy(triggeredBy);
        return entity;
    }

    private String buildIssueAlertGenerationKey(PaymentIssueAlertCandidateDTO candidate) {
        if (hasText(candidate.getSourceAlertNo())) {
            return ISSUE_ESCALATION_GENERATION_KEY_PREFIX + candidate.getSourceAlertNo().trim();
        }
        return ISSUE_ACTIVE_GENERATION_KEY_PREFIX + candidate.getIssueNo().trim();
    }

    private enum IssueAlertGenerationResult {
        GENERATED,
        DUPLICATE,
        FAILED
    }

    private String buildScheduledAlertContent(PaymentIssueAlertCandidateDTO candidate) {
        String alertContent = candidate.getAlertContent();
        String scheduleTag = candidate.getScheduleTag();
        String effectiveWindow = candidate.getEffectiveWindow();
        if (!hasText(scheduleTag) && !hasText(effectiveWindow)) {
            return alertContent;
        }
        StringBuilder builder = new StringBuilder(alertContent == null ? "" : alertContent);
        builder.append("【");
        if (hasText(scheduleTag)) {
            builder.append("班次：").append(scheduleTag);
        }
        if (hasText(scheduleTag) && hasText(effectiveWindow)) {
            builder.append("，");
        }
        if (hasText(effectiveWindow)) {
            builder.append("时段：").append(effectiveWindow);
        }
        if (hasText(candidate.getEscalationReceiver())) {
            builder.append("，升级接收人：").append(candidate.getEscalationReceiver());
        }
        if (candidate.getEscalationTimeoutMinutes() != null) {
            builder.append("，").append(candidate.getEscalationTimeoutMinutes()).append("分钟未确认升级");
        }
        if (hasText(candidate.getEscalationPolicy())) {
            builder.append("，策略：").append(candidate.getEscalationPolicy());
        }
        builder.append("】");
        return builder.toString();
    }

    private String truncateIssueAlertContent(String alertContent) {
        if (alertContent == null || alertContent.length() <= ISSUE_ALERT_CONTENT_MAX_LENGTH) {
            return alertContent;
        }
        return alertContent.substring(0, ISSUE_ALERT_CONTENT_MAX_LENGTH - 3) + "...";
    }

    private PaymentTaskActionResultDTO runRepublishFailedEventsByMode(String runMode, String triggeredBy) {
        PaymentEventRetryResultDTO retryResult = paymentEventRetryTaskService.retryFailedEvents(
                RUN_MODE_AUTO.equals(runMode));
        int processedCount = retryResult.getProcessedCount();
        int successCount = retryResult.getSuccessCount();
        int failCount = retryResult.getFailCount();
        String failedEventHint = retryResult.getFailedEventNos().isEmpty()
                ? ""
                : "，失败事件：" + String.join("/", retryResult.getFailedEventNos());
        return buildAndRecordResult(
                TASK_CODE_EVENT_RETRY,
                "失败事件重发",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
                0,
                failCount,
                processedCount == 0
                        ? "当前没有失败事件需要重发。"
                        : String.format("已处理 %d 条失败事件，成功重发 %d 条，失败 %d 条%s。",
                        processedCount, successCount, failCount, failedEventHint)
        );
    }

    private PaymentTaskActionResultDTO runPendingPaymentQueriesByMode(String runMode, String triggeredBy) {
        if (paymentService == null) {
            throw new IllegalStateException("payment service is required for pending payment queries");
        }
        List<String> paymentOrderIds = RUN_MODE_MANUAL.equals(runMode)
                ? paymentTaskCenterMapper.findPendingPaymentOrderIdsForManualQuery(
                        pendingQueryMinAgeSeconds,
                        pendingQueryPerChannelBatchSize,
                        pendingQueryBatchSize)
                : paymentTaskCenterMapper.findDuePendingPaymentOrderIdsForQuery(
                        pendingQueryMinAgeSeconds,
                        pendingQueryRetryIntervalSeconds,
                        pendingQueryRetryBackoffFactor,
                        pendingQueryMaxRetryIntervalSeconds,
                        pendingQueryPerChannelBatchSize,
                        pendingQueryBatchSize);
        int successCount = 0;
        int warningCount = 0;
        int failCount = 0;
        List<String> failedPaymentOrderIds = new ArrayList<>();
        for (String paymentOrderId : paymentOrderIds) {
            PaymentQueryRequestDTO request = new PaymentQueryRequestDTO();
            request.setPaymentOrderId(paymentOrderId);
            try {
                PaymentDetailDTO result = paymentService.query(request);
                if (result != null && "SUCCESS".equalsIgnoreCase(result.getStatus())) {
                    successCount++;
                } else {
                    warningCount++;
                }
            } catch (RuntimeException exception) {
                failCount++;
                if (failedPaymentOrderIds.size() < 3) {
                    failedPaymentOrderIds.add(paymentOrderId);
                }
            }
        }
        String failedPaymentHint = failedPaymentOrderIds.isEmpty()
                ? ""
                : "，失败支付单：" + String.join("/", failedPaymentOrderIds);
        return buildAndRecordResult(
                TASK_CODE_PENDING_QUERY,
                "待回调支付主动查单",
                runMode,
                triggeredBy,
                paymentOrderIds.size(),
                successCount,
                warningCount,
                failCount,
                paymentOrderIds.isEmpty()
                        ? "当前没有达到补查条件的待回调支付单。"
                        : String.format("已补查 %d 笔待回调支付单，成功收口 %d 笔，仍待回调 %d 笔，查询失败 %d 笔%s。",
                        paymentOrderIds.size(), successCount, warningCount, failCount, failedPaymentHint)
        );
    }

    private PaymentTaskActionResultDTO runPendingRefundQueriesByMode(String runMode, String triggeredBy) {
        if (refundQueryTaskService == null) {
            throw new IllegalStateException("refund query task service is required for pending refund queries");
        }
        RefundQueryTaskResultDTO queryResult = RUN_MODE_MANUAL.equals(runMode)
                ? refundQueryTaskService.queryPendingRefunds()
                : refundQueryTaskService.queryDueRefunds();
        String failedRefundHint = queryResult.getFailedRefundOrderIds().isEmpty()
                ? ""
                : "，查询失败退款单：" + String.join("/", queryResult.getFailedRefundOrderIds());
        return buildAndRecordResult(
                TASK_CODE_REFUND_PENDING_QUERY,
                "处理中退款主动查单",
                runMode,
                triggeredBy,
                queryResult.getProcessedCount(),
                queryResult.getSuccessCount(),
                queryResult.getWarningCount(),
                queryResult.getFailCount(),
                queryResult.getProcessedCount() == 0
                        ? "当前没有达到补查条件的处理中退款。"
                        : String.format("已补查 %d 笔处理中退款，成功收口 %d 笔，仍待跟进 %d 笔，查询失败 %d 笔%s。",
                        queryResult.getProcessedCount(),
                        queryResult.getSuccessCount(),
                        queryResult.getWarningCount(),
                        queryResult.getFailCount(),
                        failedRefundHint)
        );
    }

    private PaymentTaskActionResultDTO runRetryFailedRefundsByMode(String runMode, String triggeredBy) {
        RefundRetryResultDTO retryResult = refundRetryTaskService.retryFailedRefunds(triggeredBy);
        int processedCount = retryResult.getProcessedCount();
        int successCount = retryResult.getSuccessCount();
        int failCount = retryResult.getFailCount();
        String failedRefundHint = retryResult.getFailedRefundOrderIds().isEmpty()
                ? ""
                : "，失败退款单：" + String.join("/", retryResult.getFailedRefundOrderIds());
        return buildAndRecordResult(
                TASK_CODE_REFUND_RETRY,
                "失败退款重试",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
                0,
                failCount,
                processedCount == 0
                        ? "当前没有失败退款需要重试。"
                        : String.format("已重试 %d 笔失败退款，成功提交渠道 %d 笔，失败 %d 笔%s。",
                        processedCount, successCount, failCount, failedRefundHint)
        );
    }

    private PaymentTaskActionResultDTO buildAndRecordResult(String taskCode,
                                                            String taskName,
                                                            String runMode,
                                                            String triggeredBy,
                                                            int processedCount,
                                                            int successCount,
                                                            int warningCount,
                                                            int failCount,
                                                            String summaryComment) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo(buildTaskLogNo());
        entity.setTaskCode(taskCode);
        entity.setTaskName(taskName);
        entity.setRunMode(runMode);
        entity.setTaskStatus(failCount > 0 || warningCount > 0 ? TASK_STATUS_WARN : TASK_STATUS_SUCCESS);
        entity.setTaskStatusType(failCount > 0 || warningCount > 0 ? "warn" : "success");
        entity.setSeverityLevel(resolveSeverityLevel(taskCode, warningCount, failCount, processedCount));
        entity.setSeverityLevelType(resolveSeverityType(taskCode, warningCount, failCount, processedCount));
        entity.setEscalationStatus(resolveEscalationStatus(taskCode, warningCount, failCount, processedCount));
        entity.setEscalationStatusType(resolveEscalationType(taskCode, warningCount, failCount, processedCount));
        entity.setProcessedCount(processedCount);
        entity.setSuccessCount(successCount);
        entity.setWarningCount(warningCount);
        entity.setFailCount(failCount);
        entity.setSummaryComment(summaryComment);
        entity.setSuggestedAction(resolveSuggestedAction(taskCode, warningCount, failCount, processedCount));
        entity.setRecommendedRoute(resolveRecommendedRoute(taskCode));
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setWarningCount(warningCount);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        result.setOverview(overview());
        return result;
    }

    private PaymentTaskActionResultDTO runCloseExpiredPaymentsByMode(String runMode, String triggeredBy, String modeLabel) {
        PaymentExpiryCloseResultDTO closeResult = paymentExpiryTaskService.closeExpiredPayments();
        int processedCount = closeResult.getProcessedCount();
        int successCount = closeResult.getSuccessCount();
        int failCount = closeResult.getFailCount();
        String failedPaymentHint = closeResult.getFailedPaymentOrderIds().isEmpty()
                ? ""
                : "，失败支付单：" + String.join("/", closeResult.getFailedPaymentOrderIds());
        return buildAndRecordResult(
                TASK_CODE_EXPIRE_CLOSE,
                "支付超时关单",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
                0,
                failCount,
                processedCount == 0
                        ? "当前没有待关闭的超时支付单。"
                        : String.format("%s处理 %d 笔超时支付单，成功关闭 %d 笔，失败 %d 笔%s。",
                        modeLabel, processedCount, successCount, failCount, failedPaymentHint)
        );
    }

    private PaymentTaskActionResultDTO buildLeaseSkippedResult(String taskCode,
                                                               String taskName,
                                                               String runMode,
                                                               String triggeredBy) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo(buildTaskLogNo());
        entity.setTaskCode(taskCode);
        entity.setTaskName(taskName);
        entity.setRunMode(runMode);
        entity.setTaskStatus(TASK_STATUS_WARN);
        entity.setTaskStatusType("warn");
        entity.setSeverityLevel("P3");
        entity.setSeverityLevelType("warn");
        entity.setEscalationStatus("其他实例执行中");
        entity.setEscalationStatusType("warn");
        entity.setProcessedCount(0);
        entity.setSuccessCount(0);
        entity.setWarningCount(0);
        entity.setFailCount(0);
        entity.setSummaryComment("检测到其他实例已持有任务租约，本次任务跳过执行。");
        entity.setSuggestedAction("继续观察当前实例执行结果，避免重复调度同一任务。");
        entity.setRecommendedRoute(resolveRecommendedRoute(taskCode));
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setProcessedCount(0);
        result.setSuccessCount(0);
        result.setWarningCount(0);
        result.setFailCount(0);
        result.setSummaryComment("检测到其他实例已持有任务租约，本次任务跳过执行。");
        result.setOverview(overview());
        return result;
    }

    private interface AutoTaskRunner {
        PaymentTaskActionResultDTO run();
    }

    private List<PaymentAlertItemDTO> buildFocusAlerts(PaymentTaskCenterOverviewDTO overview) {
        List<PaymentAlertItemDTO> alerts = new ArrayList<>();
        int expiredPaymentCount = valueOrZero(overview.getExpiredPaymentCount());
        int pendingCallbackCount = valueOrZero(overview.getPendingCallbackCount());
        int pendingQueryBacklogCount = valueOrZero(overview.getPendingQueryBacklogCount());
        int duePendingQueryCount = valueOrZero(overview.getDuePendingQueryCount());
        int recentQueryCount = valueOrZero(overview.getRecentQueryCount());
        int recentQueryFailureCount = valueOrZero(overview.getRecentQueryFailureCount());
        int recentQueryRateLimitRejectionCount = valueOrZero(
                overview.getRecentQueryRateLimitRejectionCount());
        double recentQueryFailureRate = percentage(recentQueryFailureCount, recentQueryCount);
        int failedEventCount = valueOrZero(overview.getFailedEventCount());
        int unknownRefundSubmitEventCount = valueOrZero(overview.getUnknownRefundSubmitEventCount());
        int pendingIssueAlertDispatchCount = valueOrZero(overview.getPendingIssueAlertDispatchCount());
        int failedIssueAlertDispatchCount = valueOrZero(overview.getFailedIssueAlertDispatchCount());
        int oldestPendingIssueAlertDispatchMinutes =
                valueOrZero(overview.getOldestPendingIssueAlertDispatchMinutes());
        int pendingIssueAlertReceiptCount = valueOrZero(overview.getPendingIssueAlertReceiptCount());
        int oldestPendingIssueAlertReceiptMinutes =
                valueOrZero(overview.getOldestPendingIssueAlertReceiptMinutes());
        boolean issueAlertDispatchThresholdExceeded = failedIssueAlertDispatchCount > 0
                || pendingIssueAlertDispatchCount >= issueAlertDispatchCountThreshold
                || (pendingIssueAlertDispatchCount > 0
                && oldestPendingIssueAlertDispatchMinutes >= issueAlertDispatchTimeoutMinutes);
        boolean issueAlertReceiptThresholdExceeded = pendingIssueAlertReceiptCount >= issueAlertReceiptCountThreshold
                || (pendingIssueAlertReceiptCount > 0
                && oldestPendingIssueAlertReceiptMinutes >= issueAlertReceiptTimeoutMinutes);
        alerts.add(buildAlert(
                "PAYMENT_EXPIRY",
                "超时支付待关闭",
                expiredPaymentCount,
                expiredPaymentCount > 0 ? "存在超时但未收口的支付单，建议先执行超时关单" : "暂无超时支付待关闭",
                "/payment-task-center",
                expiredPaymentCount > 10 ? "P1" : "P2",
                expiredPaymentCount > 10 ? "danger" : "warn"
        ));
        alerts.add(buildAlert(
                "PAYMENT_CALLBACK",
                "待收口支付中",
                pendingCallbackCount,
                pendingCallbackCount > 0 ? "存在待回调支付单，建议优先查单和回调收口" : "暂无待收口支付",
                "/payment-issues?issueType=待回调未收口",
                pendingCallbackCount > 10 ? "P1" : "P2",
                pendingCallbackCount > 10 ? "danger" : "warn"
        ));
        alerts.add(buildAlert(
                "PAYMENT_QUERY_BACKLOG",
                "主动查单积压",
                pendingQueryBacklogCount,
                pendingQueryBacklogCount > 0
                        ? String.format("%d 笔已达到补查阈值，其中 %d 笔当前已到自动查询时间",
                        pendingQueryBacklogCount, duePendingQueryCount)
                        : "暂无达到补查阈值的支付单",
                "/payment-task-center",
                pendingQueryBacklogCount > pendingQueryBatchSize
                        ? "P1"
                        : pendingQueryBacklogCount > 0 ? "P2" : "P3",
                pendingQueryBacklogCount > pendingQueryBatchSize
                        ? "danger"
                        : pendingQueryBacklogCount > 0 ? "warn" : "success"
        ));
        boolean queryFailureThresholdExceeded = recentQueryFailureCount >= queryFailureCountThreshold
                || (recentQueryCount >= queryFailureRateMinimumSampleCount
                && recentQueryFailureRate >= queryFailureRateThresholdPercent);
        alerts.add(buildAlert(
                "PAYMENT_QUERY_FAILURE_TREND",
                "主动查单失败趋势",
                recentQueryFailureCount,
                String.format("近 %d 小时支付与退款查单 %d 次，查询失败 %d 次（%s）",
                        queryMonitorLookbackHours,
                        recentQueryCount,
                        recentQueryFailureCount,
                        formatPercentage(recentQueryFailureCount, recentQueryCount)),
                "/payment-task-center",
                queryFailureThresholdExceeded ? "P1" : recentQueryFailureCount > 0 ? "P2" : "P3",
                queryFailureThresholdExceeded ? "danger" : recentQueryFailureCount > 0 ? "warn" : "success"
        ));
        alerts.add(buildAlert(
                "PAYMENT_QUERY_RATE_LIMIT_REJECTION",
                "主动查单限流拒绝",
                recentQueryRateLimitRejectionCount,
                String.format("近 %d 小时共有 %d 次支付或退款查单因共享渠道配额耗尽被拒绝",
                        queryMonitorLookbackHours,
                        recentQueryRateLimitRejectionCount),
                "/payment-task-center",
                recentQueryRateLimitRejectionCount >= queryRateLimitRejectionCountThreshold
                        ? "P1"
                        : recentQueryRateLimitRejectionCount > 0 ? "P2" : "P3",
                recentQueryRateLimitRejectionCount >= queryRateLimitRejectionCountThreshold
                        ? "danger"
                        : recentQueryRateLimitRejectionCount > 0 ? "warn" : "success"
        ));
        alerts.add(buildAlert(
                "PAYMENT_EVENT",
                "失败事件待重发",
                failedEventCount,
                failedEventCount > 0 ? "存在失败事件，建议重发后回看下游收口" : "暂无失败事件",
                "/payment-task-center",
                failedEventCount > 5 ? "P1" : "P2",
                failedEventCount > 5 ? "danger" : "warn"
        ));
        alerts.add(buildAlert(
                "REFUND_SUBMIT_UNKNOWN",
                "退款提交结果待核查",
                unknownRefundSubmitEventCount,
                unknownRefundSubmitEventCount > 0
                        ? "存在可能已被渠道受理的退款提交，必须先查单或人工核查，禁止直接重发"
                        : "暂无退款提交结果未知事件",
                "/payment-issues?issueType=退款提交结果待核查",
                unknownRefundSubmitEventCount > 0 ? "P1" : "P3",
                unknownRefundSubmitEventCount > 0 ? "danger" : "success"
        ));
        alerts.add(buildAlert(
                "PAYMENT_ISSUE_ALERT_DISPATCH_BACKLOG",
                "异常告警待派发",
                pendingIssueAlertDispatchCount,
                pendingIssueAlertDispatchCount > 0
                        ? String.format("当前有 %d 条来源 outbox 等待派发，其中 %d 条仍存在派发异常，最老已等待 %d 分钟",
                        pendingIssueAlertDispatchCount,
                        failedIssueAlertDispatchCount,
                        oldestPendingIssueAlertDispatchMinutes)
                        : "暂无待派发异常告警",
                "/payment-issue-alerts",
                issueAlertDispatchThresholdExceeded ? "P1" : pendingIssueAlertDispatchCount > 0 ? "P2" : "P3",
                issueAlertDispatchThresholdExceeded ? "danger" : pendingIssueAlertDispatchCount > 0 ? "warn" : "success"
        ));
        alerts.add(buildAlert(
                "PAYMENT_ISSUE_ALERT_RECEIPT_BACKLOG",
                "异常告警待回查",
                pendingIssueAlertReceiptCount,
                pendingIssueAlertReceiptCount > 0
                        ? String.format("当前有 %d 条供应商已受理告警等待送达回执，最老已等待 %d 分钟",
                        pendingIssueAlertReceiptCount,
                        oldestPendingIssueAlertReceiptMinutes)
                        : "暂无待回查告警回执",
                "/payment-issue-alerts",
                issueAlertReceiptThresholdExceeded ? "P1" : pendingIssueAlertReceiptCount > 0 ? "P2" : "P3",
                issueAlertReceiptThresholdExceeded ? "danger" : pendingIssueAlertReceiptCount > 0 ? "warn" : "success"
        ));
        int overdueIssueCount = valueOrZero(overview.getOverdueIssueCount());
        int controlPolicyWarningCount = valueOrZero(overview.getControlPolicyWarningCount());
        alerts.add(buildAlert(
                "PAYMENT_ISSUE_SLA",
                "异常 SLA 超时",
                overdueIssueCount,
                overdueIssueCount > 0 ? "存在超过 SLA 的支付交易异常，建议执行升级巡检并分派责任人" : "暂无 SLA 超时异常",
                "/payment-issues",
                overdueIssueCount > 0 ? "P1" : "P3",
                overdueIssueCount > 0 ? "danger" : "success"
        ));
        alerts.add(buildAlert(
                "PAYMENT_CONTROL_POLICY",
                "支付控制策略待收敛",
                controlPolicyWarningCount,
                controlPolicyWarningCount > 0 ? "存在未通过自检的控制策略，建议立即执行巡检并回配置中心收敛" : "暂无未通过自检的控制策略",
                "/payment-task-center",
                controlPolicyWarningCount > 0 ? "P2" : "P3",
                controlPolicyWarningCount > 0 ? "warn" : "success"
        ));
        return alerts;
    }

    private PaymentAlertItemDTO buildAlert(String alertType,
                                           String alertTitle,
                                           Integer affectedCount,
                                           String alertMessage,
                                           String actionRoute,
                                           String alertLevel,
                                           String alertLevelType) {
        PaymentAlertItemDTO alert = new PaymentAlertItemDTO();
        alert.setAlertType(alertType);
        alert.setAlertTitle(alertTitle);
        alert.setAffectedCount(affectedCount == null ? 0 : affectedCount);
        alert.setAlertMessage(alertMessage);
        alert.setAlertLevel(alertLevel);
        alert.setAlertLevelType(alertLevelType);
        alert.setSuggestedAction("进入任务中心或异常中心进行处理");
        alert.setActionRoute(actionRoute);
        return alert;
    }

    private String formatPercentage(int numerator, int denominator) {
        return String.format(Locale.ROOT, "%.2f%%", percentage(numerator, denominator));
    }

    private double percentage(int numerator, int denominator) {
        return denominator <= 0 ? 0D : numerator * 100D / denominator;
    }

    private String resolveSeverityLevel(String taskCode, int warningCount, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, warningCount, failCount, processedCount)) {
            return "P1";
        }
        if (shouldFocusOnDuty(taskCode, warningCount, failCount, processedCount)) {
            return "P2";
        }
        return "P3";
    }

    private String resolveSeverityType(String taskCode, int warningCount, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, warningCount, failCount, processedCount)) {
            return "danger";
        }
        if (shouldFocusOnDuty(taskCode, warningCount, failCount, processedCount)) {
            return "warn";
        }
        return "success";
    }

    private String resolveEscalationStatus(String taskCode, int warningCount, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, warningCount, failCount, processedCount)) {
            return "升级值班负责人";
        }
        if (shouldFocusOnDuty(taskCode, warningCount, failCount, processedCount)) {
            return "纳入当班跟进";
        }
        return "正常";
    }

    private String resolveEscalationType(String taskCode, int warningCount, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, warningCount, failCount, processedCount)) {
            return "danger";
        }
        if (shouldFocusOnDuty(taskCode, warningCount, failCount, processedCount)) {
            return "warn";
        }
        return "success";
    }

    private String resolveSuggestedAction(String taskCode, int warningCount, int failCount, int processedCount) {
        if (TASK_CODE_EXPIRE_CLOSE.equals(taskCode)) {
            if (failCount > 0) {
                return "先核对超时支付单状态机与收银台过期时间，再补关单原因并复跑任务";
            }
            if (processedCount >= 20) {
                return "批量关单量较大，建议复核是否存在回调延迟或收银台过期参数异常";
            }
            return processedCount > 0 ? "已完成超时收口，继续观察新进入队列的支付单" : "暂无超时支付待处理";
        }
        if (TASK_CODE_PENDING_QUERY.equals(taskCode)) {
            if (failCount > 0) {
                return "优先核对 gateway-access 可用性、渠道凭证和查单返回码，再补跑主动查单任务";
            }
            if (warningCount > 0) {
            return "仍有渠道处理中支付单，继续等待回调并按重试间隔补查，收银台过期后仍以渠道结果收口";
            }
            return processedCount > 0 ? "已完成待回调支付收口，继续观察渠道回调与下游事件" : "暂无待回调支付需要补查";
        }
        if (TASK_CODE_REFUND_PENDING_QUERY.equals(taskCode)) {
            if (failCount > 0) {
                return "优先核对 gateway-access 退款查单实现、渠道凭证和渠道退款号，再补跑退款查单任务";
            }
            if (warningCount > 0) {
                return "仍有处理中或失败退款，继续等待可信回调并按退避窗口补查，失败终态需复核后再重试";
            }
            return processedCount > 0 ? "已完成退款成功收口，继续核对到账与后续账务事实" : "暂无处理中退款需要补查";
        }
        if (TASK_CODE_QUERY_MONITOR_ALERT.equals(taskCode)) {
            return processedCount > 0
                    ? "进入支付异常中心确认主动查单监控告警，并按既有派发任务通知渠道运营和值班人员"
                    : "当前主动查单监控指标未达到告警阈值";
        }
        if (TASK_CODE_EVENT_RETRY.equals(taskCode)) {
            if (failCount > 0) {
                return "优先核对出站事件主题、下游订阅状态与重发表，必要时升级账务/清分值班";
            }
            return processedCount > 0 ? "重发后需复核下游是否完成收口，避免跨系统状态分叉" : "暂无失败事件待处理";
        }
        if (TASK_CODE_REFUND_RETRY.equals(taskCode)) {
            if (failCount > 0) {
                return "优先核对退款渠道响应、退款单状态和逆向账务，必要时转财务人工补退";
            }
            return processedCount > 0 ? "重试后需继续跟踪退款回调与用户到账结果" : "暂无失败退款待处理";
        }
        if (TASK_CODE_ISSUE_ESCALATE.equals(taskCode)) {
            return processedCount > 0 ? "立即进入异常中心，按 SLA 超时列表分派值班负责人并补充处理备注" : "暂无 SLA 超时异常";
        }
        if (TASK_CODE_CONTROL_SELF_CHECK.equals(taskCode)) {
            if (failCount > 0) {
                return "优先进入支付配置中心收敛渠道、网关、商户号或令牌配置，再复跑控制策略巡检";
            }
            if (warningCount > 0) {
                return "存在控制策略告警，建议先核对渠道覆盖、网关启用状态和令牌完整性，再复跑巡检";
            }
            return processedCount > 0 ? "本轮控制策略已完成巡检，建议继续观察新接入应用和渠道变更" : "暂无启用中的支付控制策略待巡检";
        }
        if (TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE.equals(taskCode)) {
            if (failCount > 0) {
                return "优先核对供应商回执状态、告警日志并发更新结果，再执行回执回查补偿";
            }
            return processedCount > 0 ? "已完成供应商回执回查，建议继续观察异常中心的告警收口状态" : "暂无需要回查的供应商告警回执";
        }
        return processedCount > 0 ? "优先处理异常明细并确认下游收口" : "暂无待处理任务";
    }

    private String resolveRecommendedRoute(String taskCode) {
        if (TASK_CODE_EXPIRE_CLOSE.equals(taskCode)) {
            return "/payment-task-center";
        }
        if (TASK_CODE_PENDING_QUERY.equals(taskCode)) {
            return "/payments";
        }
        if (TASK_CODE_REFUND_PENDING_QUERY.equals(taskCode)) {
            return "/refunds";
        }
        if (TASK_CODE_QUERY_MONITOR_ALERT.equals(taskCode)) {
            return "/payment-issues";
        }
        if (TASK_CODE_EVENT_RETRY.equals(taskCode)) {
            return "/payment-events";
        }
        if (TASK_CODE_REFUND_RETRY.equals(taskCode)) {
            return "/refunds";
        }
        if (TASK_CODE_ISSUE_ESCALATE.equals(taskCode)) {
            return "/payment-issues";
        }
        if (TASK_CODE_CONTROL_SELF_CHECK.equals(taskCode)) {
            return "/payment-config";
        }
        if (TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE.equals(taskCode)) {
            return "/payment-issues";
        }
        return "/payment-task-center";
    }

    private List<PaymentTaskRunLogItemDTO> enrichTaskRuns(List<PaymentTaskRunLogItemDTO> items) {
        for (PaymentTaskRunLogItemDTO item : items) {
            item.setSeverityLevel(resolveSeverityLevel(item.getTaskCode(), valueOrZero(item.getWarningCount()), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSeverityLevelType(resolveSeverityType(item.getTaskCode(), valueOrZero(item.getWarningCount()), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatus(resolveEscalationStatus(item.getTaskCode(), valueOrZero(item.getWarningCount()), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatusType(resolveEscalationType(item.getTaskCode(), valueOrZero(item.getWarningCount()), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSuggestedAction(resolveSuggestedAction(item.getTaskCode(), valueOrZero(item.getWarningCount()), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setRecommendedRoute(resolveRecommendedRoute(item.getTaskCode()));
        }
        return items;
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private String valueOf(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 根据任务类型、失败量和处理规模推导是否需要立即升级。
     */
    private boolean shouldEscalateImmediately(String taskCode, int warningCount, int failCount, int processedCount) {
        if (TASK_CODE_QUERY_MONITOR_ALERT.equals(taskCode)) {
            return processedCount > 0;
        }
        if (TASK_CODE_ISSUE_ESCALATE.equals(taskCode)) {
            return processedCount > 0;
        }
        if (TASK_CODE_CONTROL_SELF_CHECK.equals(taskCode)) {
            return failCount > 0;
        }
        if (TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE.equals(taskCode)) {
            return false;
        }
        if (failCount <= 0) {
            return false;
        }
        if (TASK_CODE_EVENT_RETRY.equals(taskCode)) {
            return failCount >= 3 || processedCount >= 10;
        }
        if (TASK_CODE_REFUND_RETRY.equals(taskCode)) {
            return failCount >= 2 || processedCount >= 8;
        }
        if (TASK_CODE_EXPIRE_CLOSE.equals(taskCode)) {
            return failCount >= 5 || processedCount >= 30;
        }
        if (TASK_CODE_PENDING_QUERY.equals(taskCode)
                || TASK_CODE_REFUND_PENDING_QUERY.equals(taskCode)) {
            return failCount >= 3;
        }
        return failCount >= 1;
    }

    /**
     * 根据任务类型判断是否需要纳入当班关注。
     */
    private boolean shouldFocusOnDuty(String taskCode, int warningCount, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, warningCount, failCount, processedCount)) {
            return false;
        }
        if (TASK_CODE_CONTROL_SELF_CHECK.equals(taskCode)) {
            return warningCount > 0;
        }
        if (TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE.equals(taskCode)) {
            return failCount > 0;
        }
        if (failCount > 0) {
            return true;
        }
        if (TASK_CODE_EVENT_RETRY.equals(taskCode)) {
            return processedCount >= 1;
        }
        if (TASK_CODE_REFUND_RETRY.equals(taskCode)) {
            return processedCount >= 1;
        }
        if (TASK_CODE_EXPIRE_CLOSE.equals(taskCode)) {
            return processedCount >= 10;
        }
        if (TASK_CODE_PENDING_QUERY.equals(taskCode)
                || TASK_CODE_REFUND_PENDING_QUERY.equals(taskCode)) {
            return warningCount > 0 || failCount > 0;
        }
        if (TASK_CODE_ISSUE_ESCALATE.equals(taskCode)) {
            return false;
        }
        return processedCount > 0;
    }

    private PaymentTaskRunLogQueryDTO normalizeQuery(PaymentTaskRunLogQueryDTO query) {
        PaymentTaskRunLogQueryDTO normalizedQuery = query == null ? new PaymentTaskRunLogQueryDTO() : query;
        if (normalizedQuery.getPageNo() < 1) {
            normalizedQuery.setPageNo(1);
        }
        if (normalizedQuery.getPageSize() < 1) {
            normalizedQuery.setPageSize(10);
        }
        if (normalizedQuery.getPageSize() > 100) {
            normalizedQuery.setPageSize(100);
        }
        return normalizedQuery;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private String buildTaskLogNo() {
        return "PTL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String buildAlertLogNo() {
        return "PIA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }
}
