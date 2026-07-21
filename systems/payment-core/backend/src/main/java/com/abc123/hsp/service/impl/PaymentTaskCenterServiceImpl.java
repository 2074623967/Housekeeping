package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final PaymentExpiryTaskService paymentExpiryTaskService;
    private final PaymentEventMapper paymentEventMapper;
    private final RefundMapper refundMapper;

    public PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                        PaymentExpiryTaskService paymentExpiryTaskService,
                                        PaymentEventMapper paymentEventMapper,
                                        RefundMapper refundMapper) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.paymentExpiryTaskService = paymentExpiryTaskService;
        this.paymentEventMapper = paymentEventMapper;
        this.refundMapper = refundMapper;
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

    private PaymentTaskActionResultDTO runRepublishFailedEventsByMode(String runMode, String triggeredBy) {
        List<String> failedEventNos = paymentEventMapper.findFailedEventNos();
        int successCount = 0;
        for (String eventNo : failedEventNos) {
            successCount += paymentEventMapper.markRepublished(eventNo);
        }
        int failCount = Math.max(failedEventNos.size() - successCount, 0);
        return buildAndRecordResult(
                "PAYMENT_EVENT_RETRY",
                "失败事件重发",
                runMode,
                triggeredBy,
                failedEventNos.size(),
                successCount,
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
                "REFUND_FAIL_RETRY",
                "失败退款重试",
                runMode,
                triggeredBy,
                failedRefundOrderIds.size(),
                successCount,
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
                                                            int failCount,
                                                            String summaryComment) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo(buildTaskLogNo());
        entity.setTaskCode(taskCode);
        entity.setTaskName(taskName);
        entity.setRunMode(runMode);
        entity.setTaskStatus(failCount > 0 ? TASK_STATUS_WARN : TASK_STATUS_SUCCESS);
        entity.setTaskStatusType(failCount > 0 ? "warn" : "success");
        entity.setSeverityLevel(resolveSeverityLevel(failCount, processedCount));
        entity.setSeverityLevelType(resolveSeverityType(failCount, processedCount));
        entity.setEscalationStatus(resolveEscalationStatus(failCount, processedCount));
        entity.setEscalationStatusType(resolveEscalationType(failCount, processedCount));
        entity.setProcessedCount(processedCount);
        entity.setSuccessCount(successCount);
        entity.setFailCount(failCount);
        entity.setSummaryComment(summaryComment);
        entity.setSuggestedAction(resolveSuggestedAction(taskCode, failCount, processedCount));
        entity.setRecommendedRoute(resolveRecommendedRoute(taskCode));
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        result.setOverview(overview());
        return result;
    }

    private PaymentTaskActionResultDTO runCloseExpiredPaymentsByMode(String runMode, String triggeredBy, String modeLabel) {
        int successCount = paymentExpiryTaskService.closeExpiredPayments();
        int processedCount = successCount;
        return buildAndRecordResult(
                "PAYMENT_EXPIRE_CLOSE",
                "支付超时关单",
                runMode,
                triggeredBy,
                processedCount,
                successCount,
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

    private String resolveSeverityLevel(int failCount, int processedCount) {
        if (failCount > 0) {
            return "P1";
        }
        if (processedCount > 0) {
            return "P2";
        }
        return "P3";
    }

    private String resolveSeverityType(int failCount, int processedCount) {
        if (failCount > 0) {
            return "danger";
        }
        if (processedCount > 0) {
            return "warn";
        }
        return "success";
    }

    private String resolveEscalationStatus(int failCount, int processedCount) {
        if (failCount > 0) {
            return "需立即升级";
        }
        if (processedCount > 0) {
            return "需关注";
        }
        return "正常";
    }

    private String resolveEscalationType(int failCount, int processedCount) {
        if (failCount > 0) {
            return "danger";
        }
        if (processedCount > 0) {
            return "warn";
        }
        return "success";
    }

    private String resolveSuggestedAction(String taskCode, int failCount, int processedCount) {
        if ("PAYMENT_EXPIRE_CLOSE".equals(taskCode)) {
            return failCount > 0 ? "优先核对超时支付单并补充关闭原因" : "可继续观察超时关单队列";
        }
        return processedCount > 0 ? "优先处理异常明细并确认下游收口" : "暂无待处理任务";
    }

    private String resolveRecommendedRoute(String taskCode) {
        if ("PAYMENT_EXPIRE_CLOSE".equals(taskCode)) {
            return "/payment-task-center";
        }
        if ("PAYMENT_EVENT_RETRY".equals(taskCode)) {
            return "/payment-events";
        }
        if ("REFUND_FAIL_RETRY".equals(taskCode)) {
            return "/refunds";
        }
        return "/payment-task-center";
    }

    private List<PaymentTaskRunLogItemDTO> enrichTaskRuns(List<PaymentTaskRunLogItemDTO> items) {
        for (PaymentTaskRunLogItemDTO item : items) {
            item.setSeverityLevel(resolveSeverityLevel(valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSeverityLevelType(resolveSeverityType(valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatus(resolveEscalationStatus(valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatusType(resolveEscalationType(valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSuggestedAction(resolveSuggestedAction(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setRecommendedRoute(resolveRecommendedRoute(item.getTaskCode()));
        }
        return items;
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
}
