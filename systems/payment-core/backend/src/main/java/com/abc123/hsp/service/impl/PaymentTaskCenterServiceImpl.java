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
    private static final String TASK_CODE_EXPIRE_CLOSE = "PAYMENT_EXPIRE_CLOSE";
    private static final String TASK_CODE_EVENT_RETRY = "PAYMENT_EVENT_RETRY";
    private static final String TASK_CODE_REFUND_RETRY = "REFUND_FAIL_RETRY";

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
                TASK_CODE_EVENT_RETRY,
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
                TASK_CODE_REFUND_RETRY,
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
        entity.setSeverityLevel(resolveSeverityLevel(taskCode, failCount, processedCount));
        entity.setSeverityLevelType(resolveSeverityType(taskCode, failCount, processedCount));
        entity.setEscalationStatus(resolveEscalationStatus(taskCode, failCount, processedCount));
        entity.setEscalationStatusType(resolveEscalationType(taskCode, failCount, processedCount));
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
                TASK_CODE_EXPIRE_CLOSE,
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

    private String resolveSeverityLevel(String taskCode, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, failCount, processedCount)) {
            return "P1";
        }
        if (shouldFocusOnDuty(taskCode, failCount, processedCount)) {
            return "P2";
        }
        return "P3";
    }

    private String resolveSeverityType(String taskCode, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, failCount, processedCount)) {
            return "danger";
        }
        if (shouldFocusOnDuty(taskCode, failCount, processedCount)) {
            return "warn";
        }
        return "success";
    }

    private String resolveEscalationStatus(String taskCode, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, failCount, processedCount)) {
            return "升级值班负责人";
        }
        if (shouldFocusOnDuty(taskCode, failCount, processedCount)) {
            return "纳入当班跟进";
        }
        return "正常";
    }

    private String resolveEscalationType(String taskCode, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, failCount, processedCount)) {
            return "danger";
        }
        if (shouldFocusOnDuty(taskCode, failCount, processedCount)) {
            return "warn";
        }
        return "success";
    }

    private String resolveSuggestedAction(String taskCode, int failCount, int processedCount) {
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
        return "/payment-task-center";
    }

    private List<PaymentTaskRunLogItemDTO> enrichTaskRuns(List<PaymentTaskRunLogItemDTO> items) {
        for (PaymentTaskRunLogItemDTO item : items) {
            item.setSeverityLevel(resolveSeverityLevel(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSeverityLevelType(resolveSeverityType(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatus(resolveEscalationStatus(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setEscalationStatusType(resolveEscalationType(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setSuggestedAction(resolveSuggestedAction(item.getTaskCode(), valueOrZero(item.getFailCount()), valueOrZero(item.getProcessedCount())));
            item.setRecommendedRoute(resolveRecommendedRoute(item.getTaskCode()));
        }
        return items;
    }

    /**
     * 根据任务类型、失败量和处理规模推导是否需要立即升级。
     */
    private boolean shouldEscalateImmediately(String taskCode, int failCount, int processedCount) {
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
    private boolean shouldFocusOnDuty(String taskCode, int failCount, int processedCount) {
        if (shouldEscalateImmediately(taskCode, failCount, processedCount)) {
            return false;
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
}
