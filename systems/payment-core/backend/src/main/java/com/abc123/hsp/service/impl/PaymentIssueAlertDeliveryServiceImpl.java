package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付交易异常告警派发服务默认实现。
 */
@Service
public class PaymentIssueAlertDeliveryServiceImpl implements PaymentIssueAlertDeliveryService {

    private static final String RUN_MODE_MANUAL = "MANUAL";
    private static final String RUN_MODE_AUTO = "AUTO";
    private static final String TASK_CODE_ISSUE_ALERT_DISPATCH = "PAYMENT_ISSUE_ALERT_DISPATCH";

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final List<PaymentIssueAlertNotifier> notifiers;

    public PaymentIssueAlertDeliveryServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                                List<PaymentIssueAlertNotifier> notifiers) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.notifiers = notifiers;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO dispatchPendingAlerts() {
        return dispatchPendingAlertsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO autoDispatchPendingAlerts() {
        return dispatchPendingAlertsByMode(RUN_MODE_AUTO, "payment-issue-alert-scheduler");
    }

    private PaymentTaskActionResultDTO dispatchPendingAlertsByMode(String runMode, String triggeredBy) {
        List<PaymentIssueAlertDispatchItemDTO> pendingAlerts = paymentTaskCenterMapper.findPendingOutboxAlerts();
        Map<String, PaymentIssueAlertNotifier> notifierMap = notifiers.stream()
                .collect(Collectors.toMap(PaymentIssueAlertNotifier::channelCode, notifier -> notifier, (left, right) -> left));

        int dispatchedCount = 0;
        int failCount = 0;
        for (PaymentIssueAlertDispatchItemDTO item : pendingAlerts) {
            boolean channelSuccess = dispatchToAllChannels(item, notifierMap);
            if (channelSuccess) {
                dispatchedCount++;
                PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
                entity.setAlertNo(item.getAlertNo());
                entity.setIssueNo(item.getIssueNo());
                entity.setPaymentOrderId(item.getPaymentOrderId());
                entity.setIssueType(item.getIssueType());
                entity.setSeverity(item.getSeverity());
                entity.setResponsibilityGroup(item.getResponsibilityGroup());
                entity.setAlertChannel("IN_APP_OUTBOX");
                entity.setReceiver(item.getReceiver());
                entity.setAlertStatus("已派发");
                entity.setAlertStatusType("success");
                entity.setAckStatus("待确认");
                entity.setAckStatusType("warn");
                entity.setAlertContent(item.getAlertContent());
                entity.setTriggeredBy(triggeredBy);
                paymentTaskCenterMapper.updateIssueAlertDeliveryStatus(entity);
            } else {
                failCount++;
            }
        }

        return buildResult(runMode, triggeredBy, pendingAlerts.size(), dispatchedCount, failCount,
                pendingAlerts.isEmpty() ? "当前没有待派发的异常告警。" : String.format("待派发 %d 条异常告警，已完成 %d 条派发。", pendingAlerts.size(), dispatchedCount));
    }

    private boolean dispatchToAllChannels(PaymentIssueAlertDispatchItemDTO item,
                                          Map<String, PaymentIssueAlertNotifier> notifierMap) {
        boolean success = true;
        for (PaymentIssueAlertNotifier notifier : notifierMap.values()) {
            try {
                notifier.send(item);
            } catch (RuntimeException ex) {
                success = false;
            }
        }
        return success;
    }

    private PaymentTaskActionResultDTO buildResult(String runMode,
                                                   String triggeredBy,
                                                   int processedCount,
                                                   int successCount,
                                                   int failCount,
                                                   String summaryComment) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo("TL" + System.currentTimeMillis());
        entity.setTaskCode(TASK_CODE_ISSUE_ALERT_DISPATCH);
        entity.setTaskName("异常告警派发");
        entity.setRunMode(runMode);
        entity.setTaskStatus(failCount > 0 ? "WARNING" : "SUCCESS");
        entity.setTaskStatusType(failCount > 0 ? "warn" : "success");
        entity.setSeverityLevel(failCount > 0 ? "P2" : "P3");
        entity.setSeverityLevelType(failCount > 0 ? "warn" : "success");
        entity.setEscalationStatus(failCount > 0 ? "纳入当班跟进" : "正常");
        entity.setEscalationStatusType(failCount > 0 ? "warn" : "success");
        entity.setProcessedCount(processedCount);
        entity.setSuccessCount(successCount);
        entity.setWarningCount(0);
        entity.setFailCount(failCount);
        entity.setSummaryComment(summaryComment);
        entity.setSuggestedAction(failCount > 0 ? "优先核对通知通道适配器与消息模板" : "继续保持派发队列巡检");
        entity.setRecommendedRoute("/payment-issues");
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(TASK_CODE_ISSUE_ALERT_DISPATCH);
        result.setTaskName("异常告警派发");
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setWarningCount(0);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        return result;
    }
}
