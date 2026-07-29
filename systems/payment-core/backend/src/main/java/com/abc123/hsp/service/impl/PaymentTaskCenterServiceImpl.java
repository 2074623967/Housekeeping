package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import com.abc123.hsp.service.PaymentConfigService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final String TASK_CODE_EVENT_RETRY = "PAYMENT_EVENT_RETRY";
    private static final String TASK_CODE_REFUND_RETRY = "REFUND_FAIL_RETRY";
    private static final String TASK_CODE_ISSUE_ESCALATE = "PAYMENT_ISSUE_ESCALATE";
    private static final String TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE = "PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE";
    private static final String TASK_CODE_CONTROL_SELF_CHECK = "PAYMENT_CONTROL_SELF_CHECK";

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final PaymentExpiryTaskService paymentExpiryTaskService;
    private final PaymentEventMapper paymentEventMapper;
    private final RefundMapper refundMapper;
    private final PaymentConfigService paymentConfigService;
    private final PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService;
    private final PaymentEventDispatchService paymentEventDispatchService;

    @Autowired
    public PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                        PaymentExpiryTaskService paymentExpiryTaskService,
                                        PaymentEventMapper paymentEventMapper,
                                        RefundMapper refundMapper,
                                        PaymentConfigService paymentConfigService,
                                        PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService,
                                        PaymentEventDispatchService paymentEventDispatchService) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.paymentExpiryTaskService = paymentExpiryTaskService;
        this.paymentEventMapper = paymentEventMapper;
        this.refundMapper = refundMapper;
        this.paymentConfigService = paymentConfigService;
        this.paymentIssueAlertDeliveryService = paymentIssueAlertDeliveryService;
        this.paymentEventDispatchService = paymentEventDispatchService;
    }

    PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                 PaymentExpiryTaskService paymentExpiryTaskService,
                                 PaymentEventMapper paymentEventMapper,
                                 RefundMapper refundMapper,
                                 PaymentConfigService paymentConfigService,
                                 PaymentIssueAlertDeliveryService paymentIssueAlertDeliveryService) {
        this(paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper,
                paymentConfigService,
                paymentIssueAlertDeliveryService,
                new PaymentEventDispatchService() {
                    @Override
                    public void publishPaymentSuccess(String eventNo, String paymentOrderId) {
                        // 单元测试兼容构造器默认不触发下游联动。
                    }

                    @Override
                    public boolean republish(String eventNo) {
                        return paymentEventMapper.markRepublished(eventNo) > 0;
                    }
                });
    }

    @Override
    public PaymentTaskCenterOverviewDTO overview() {
        PaymentTaskCenterOverviewDTO overview = paymentTaskCenterMapper.findOverviewSummary();
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
    @Transactional
    public PaymentTaskActionResultDTO runCloseExpiredPayments() {
        return runCloseExpiredPaymentsByMode(RUN_MODE_MANUAL, "payment-core-admin", "人工触发");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoCloseExpiredPayments() {
        return runCloseExpiredPaymentsByMode(RUN_MODE_AUTO, "payment-expiry-scheduler", "自动调度");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRepublishFailedEvents() {
        return runRepublishFailedEventsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoRepublishFailedEvents() {
        return runRepublishFailedEventsByMode(RUN_MODE_AUTO, "payment-event-scheduler");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRetryFailedRefunds() {
        return runRetryFailedRefundsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runAutoRetryFailedRefunds() {
        return runRetryFailedRefundsByMode(RUN_MODE_AUTO, "refund-retry-scheduler");
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
        return runEscalateOverdueIssuesByMode(RUN_MODE_AUTO, "payment-issue-sla-scheduler");
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
        return runControlPolicySelfChecksByMode(RUN_MODE_AUTO, "payment-control-self-check-scheduler");
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
        for (PaymentIssueAlertCandidateDTO candidate : alertCandidates) {
            generatedAlertCount += paymentTaskCenterMapper.insertIssueAlertLog(buildIssueAlertLog(candidate, triggeredBy));
        }
        for (PaymentIssueAlertCandidateDTO candidate : escalationCandidates) {
            generatedAlertCount += paymentTaskCenterMapper.insertIssueAlertLog(buildIssueAlertLog(candidate, triggeredBy));
        }
        int processedCount = overdueIssueCount + escalationCandidates.size();
        return buildAndRecordResult(
                TASK_CODE_ISSUE_ESCALATE,
                "异常 SLA 升级巡检",
                runMode,
                triggeredBy,
                processedCount,
                generatedAlertCount,
                0,
                0,
                processedCount == 0
                        ? "当前没有超过 SLA 的支付交易异常。"
                        : String.format("发现 %d 条超过 SLA 的支付交易异常，%d 条未确认告警触发升级，本次生成 %d 条告警通知。",
                        overdueIssueCount, escalationCandidates.size(), generatedAlertCount)
        );
    }

    private PaymentIssueAlertLogEntity buildIssueAlertLog(PaymentIssueAlertCandidateDTO candidate, String triggeredBy) {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo(buildAlertLogNo());
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
        entity.setAlertContent(buildScheduledAlertContent(candidate));
        entity.setTriggeredBy(triggeredBy);
        return entity;
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

    private PaymentTaskActionResultDTO runRepublishFailedEventsByMode(String runMode, String triggeredBy) {
        List<String> failedEventNos = paymentEventMapper.findFailedEventNos();
        int successCount = 0;
        for (String eventNo : failedEventNos) {
            if (paymentEventDispatchService.republish(eventNo)) {
                successCount++;
            }
        }
        int failCount = Math.max(failedEventNos.size() - successCount, 0);
        return buildAndRecordResult(
                TASK_CODE_EVENT_RETRY,
                "失败事件重发",
                runMode,
                triggeredBy,
                failedEventNos.size(),
                successCount,
                0,
                failCount,
                failedEventNos.isEmpty()
                        ? "当前没有失败事件需要重发。"
                        : String.format("已重发 %d 条失败事件，剩余失败 %d 条。", successCount, failCount)
        );
    }

    private PaymentTaskActionResultDTO runRetryFailedRefundsByMode(String runMode, String triggeredBy) {
        List<String> failedRefundOrderIds = refundMapper.findFailedRefundOrderIds();
        int successCount = 0;
        for (String refundOrderId : failedRefundOrderIds) {
            int affectedRows = refundMapper.updateRefundStatus(refundOrderId, "FAIL", "PROCESSING", "warn", false);
            successCount += affectedRows;
            if (affectedRows > 0) {
                RefundOperationLogEntity logEntity = RefundServiceImpl.buildOperationLog(
                        refundOrderId,
                        "TASK_RETRY",
                        "任务中心失败退款重试",
                        "FAIL",
                        "PROCESSING",
                        triggeredBy,
                        "任务中心批量重试失败退款"
                );
                refundMapper.insertOperationLog(logEntity);
            }
        }
        int failCount = Math.max(failedRefundOrderIds.size() - successCount, 0);
        return buildAndRecordResult(
                TASK_CODE_REFUND_RETRY,
                "失败退款重试",
                runMode,
                triggeredBy,
                failedRefundOrderIds.size(),
                successCount,
                0,
                failCount,
                failedRefundOrderIds.isEmpty()
                        ? "当前没有失败退款需要重试。"
                        : String.format("已重试 %d 笔失败退款，剩余失败 %d 笔。", successCount, failCount)
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
        int successCount = paymentExpiryTaskService.closeExpiredPayments();
        int processedCount = successCount;
        return buildAndRecordResult(
                TASK_CODE_EXPIRE_CLOSE,
                "支付超时关单",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
                0,
                0,
                successCount == 0 ? "当前没有待关闭的超时支付单。" : String.format("%s已关闭 %d 笔超时支付单。", modeLabel, successCount)
        );
    }

    private List<PaymentAlertItemDTO> buildFocusAlerts(PaymentTaskCenterOverviewDTO overview) {
        List<PaymentAlertItemDTO> alerts = new ArrayList<>();
        int expiredPaymentCount = valueOrZero(overview.getExpiredPaymentCount());
        int pendingCallbackCount = valueOrZero(overview.getPendingCallbackCount());
        int failedEventCount = valueOrZero(overview.getFailedEventCount());
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
                "PAYMENT_EVENT",
                "失败事件待重发",
                failedEventCount,
                failedEventCount > 0 ? "存在失败事件，建议重发后回看下游收口" : "暂无失败事件",
                "/payment-task-center",
                failedEventCount > 5 ? "P1" : "P2",
                failedEventCount > 5 ? "danger" : "warn"
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

    /**
     * 根据任务类型、失败量和处理规模推导是否需要立即升级。
     */
    private boolean shouldEscalateImmediately(String taskCode, int warningCount, int failCount, int processedCount) {
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
