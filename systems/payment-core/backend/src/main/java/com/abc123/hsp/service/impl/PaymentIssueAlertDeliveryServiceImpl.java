package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付交易异常告警派发服务默认实现。
 */
@Service
public class PaymentIssueAlertDeliveryServiceImpl implements PaymentIssueAlertDeliveryService {

    private static final String RUN_MODE_MANUAL = "MANUAL";
    private static final String RUN_MODE_AUTO = "AUTO";
    private static final String TASK_CODE_ISSUE_ALERT_DISPATCH = "PAYMENT_ISSUE_ALERT_DISPATCH";
    private static final String SOURCE_CHANNEL_OUTBOX = "IN_APP_OUTBOX";
    private static final String ROUTE_CHANNEL_IN_APP = "IN_APP";

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

        int successCount = 0;
        int warningCount = 0;
        int failCount = 0;
        for (PaymentIssueAlertDispatchItemDTO item : pendingAlerts) {
            DispatchOutcome outcome = dispatchToAllChannels(item, notifierMap, triggeredBy);
            if (outcome.isAllSucceeded()) {
                successCount++;
            } else if (outcome.hasAnySuccess()) {
                warningCount++;
            } else {
                failCount++;
            }
            paymentTaskCenterMapper.updateIssueAlertDeliveryStatus(buildSourceAlertStatus(item, triggeredBy, outcome));
        }

        return buildResult(
                runMode,
                triggeredBy,
                pendingAlerts.size(),
                successCount,
                warningCount,
                failCount,
                buildSummaryComment(pendingAlerts.size(), successCount, warningCount, failCount),
                resolveHighestEscalationLevel(pendingAlerts)
        );
    }

    private DispatchOutcome dispatchToAllChannels(PaymentIssueAlertDispatchItemDTO item,
                                                  Map<String, PaymentIssueAlertNotifier> notifierMap,
                                                  String triggeredBy) {
        List<String> configuredChannels = resolveConfiguredChannels(item.getNotifyChannels());
        if (configuredChannels.isEmpty()) {
            return DispatchOutcome.noExternalDispatch();
        }
        int successCount = 0;
        int failCount = 0;
        for (String channelCode : configuredChannels) {
            if (paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery(item.getIssueNo(), channelCode)) {
                successCount++;
                continue;
            }
            PaymentIssueAlertNotifier notifier = notifierMap.get(channelCode);
            if (notifier == null) {
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(item, triggeredBy, channelCode, "派发失败", "danger"));
                failCount++;
                continue;
            }
            try {
                notifier.send(item);
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(item, triggeredBy, channelCode, "已派发", "success"));
                successCount++;
            } catch (RuntimeException ex) {
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(item, triggeredBy, channelCode, "派发失败", "danger"));
                failCount++;
            }
        }
        return new DispatchOutcome(successCount, failCount, configuredChannels.size(), false);
    }

    private PaymentIssueAlertLogEntity buildSourceAlertStatus(PaymentIssueAlertDispatchItemDTO item,
                                                              String triggeredBy,
                                                              DispatchOutcome outcome) {
        PaymentIssueAlertLogEntity entity = buildBaseAlertLog(item, triggeredBy);
        entity.setAlertNo(item.getAlertNo());
        entity.setAlertChannel(SOURCE_CHANNEL_OUTBOX);
        if (outcome.isAllSucceeded()) {
            entity.setAlertStatus("已派发");
            entity.setAlertStatusType("success");
            entity.setAckStatus("待确认");
            entity.setAckStatusType("warn");
        } else if (outcome.hasAnySuccess()) {
            entity.setAlertStatus("部分失败");
            entity.setAlertStatusType("warn");
            entity.setAckStatus("待确认");
            entity.setAckStatusType("warn");
        } else {
            entity.setAlertStatus("派发失败");
            entity.setAlertStatusType("danger");
            entity.setAckStatus("无需回执");
            entity.setAckStatusType("info");
        }
        return entity;
    }

    private PaymentIssueAlertLogEntity buildDeliveryLog(PaymentIssueAlertDispatchItemDTO item,
                                                        String triggeredBy,
                                                        String channelCode,
                                                        String alertStatus,
                                                        String alertStatusType) {
        PaymentIssueAlertLogEntity entity = buildBaseAlertLog(item, triggeredBy);
        entity.setAlertNo(buildDeliveryAlertLogNo(channelCode));
        entity.setAlertChannel(channelCode);
        entity.setAlertStatus(alertStatus);
        entity.setAlertStatusType(alertStatusType);
        entity.setAckStatus("无需回执");
        entity.setAckStatusType("info");
        return entity;
    }

    private PaymentIssueAlertLogEntity buildBaseAlertLog(PaymentIssueAlertDispatchItemDTO item, String triggeredBy) {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setIssueNo(item.getIssueNo());
        entity.setPaymentOrderId(item.getPaymentOrderId());
        entity.setIssueType(item.getIssueType());
        entity.setSeverity(item.getSeverity());
        entity.setResponsibilityGroup(item.getResponsibilityGroup());
        entity.setReceiver(item.getReceiver());
        entity.setAlertContent(item.getAlertContent());
        entity.setTriggeredBy(triggeredBy);
        return entity;
    }

    private List<String> resolveConfiguredChannels(String notifyChannels) {
        List<String> channels = new ArrayList<String>();
        if (!StringUtils.hasText(notifyChannels)) {
            return channels;
        }
        for (String channel : Arrays.asList(notifyChannels.split(","))) {
            if (!StringUtils.hasText(channel)) {
                continue;
            }
            String normalizedChannel = channel.trim().toUpperCase();
            if (ROUTE_CHANNEL_IN_APP.equals(normalizedChannel)) {
                continue;
            }
            channels.add(normalizedChannel);
        }
        return channels;
    }

    private String resolveHighestEscalationLevel(List<PaymentIssueAlertDispatchItemDTO> pendingAlerts) {
        Map<String, Integer> levelWeight = new HashMap<String, Integer>();
        levelWeight.put("L1", Integer.valueOf(1));
        levelWeight.put("L2", Integer.valueOf(2));
        levelWeight.put("L3", Integer.valueOf(3));
        String highestLevel = "L1";
        for (PaymentIssueAlertDispatchItemDTO pendingAlert : pendingAlerts) {
            String level = StringUtils.hasText(pendingAlert.getEscalationLevel())
                    ? pendingAlert.getEscalationLevel().trim().toUpperCase()
                    : "L1";
            if (levelWeight.containsKey(level)
                    && levelWeight.get(level).intValue() > levelWeight.get(highestLevel).intValue()) {
                highestLevel = level;
            }
        }
        return highestLevel;
    }

    private String buildDeliveryAlertLogNo(String channelCode) {
        return "PIA-" + channelCode + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private String buildSummaryComment(int processedCount, int successCount, int warningCount, int failCount) {
        if (processedCount == 0) {
            return "当前没有待派发的异常告警。";
        }
        return String.format(
                "待派发/补发 %d 条异常告警，完全成功 %d 条，部分失败 %d 条，全部失败 %d 条。",
                processedCount,
                successCount,
                warningCount,
                failCount
        );
    }

    private PaymentTaskActionResultDTO buildResult(String runMode,
                                                   String triggeredBy,
                                                   int processedCount,
                                                   int successCount,
                                                   int warningCount,
                                                   int failCount,
                                                   String summaryComment,
                                                   String highestEscalationLevel) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo("TL" + System.currentTimeMillis());
        entity.setTaskCode(TASK_CODE_ISSUE_ALERT_DISPATCH);
        entity.setTaskName("异常告警派发");
        entity.setRunMode(runMode);
        entity.setTaskStatus(failCount > 0 || warningCount > 0 ? "WARNING" : "SUCCESS");
        entity.setTaskStatusType(failCount > 0 || warningCount > 0 ? "warn" : "success");
        entity.setSeverityLevel(failCount > 0 ? "P1" : warningCount > 0 ? "P2" : "P3");
        entity.setSeverityLevelType(failCount > 0 ? "danger" : warningCount > 0 ? "warn" : "success");
        entity.setEscalationStatus(failCount > 0
                ? "升级至" + highestEscalationLevel + "值班负责人"
                : warningCount > 0
                ? "纳入" + highestEscalationLevel + "当班跟进"
                : "正常");
        entity.setEscalationStatusType(failCount > 0 ? "danger" : warningCount > 0 ? "warn" : "success");
        entity.setProcessedCount(processedCount);
        entity.setSuccessCount(successCount);
        entity.setWarningCount(warningCount);
        entity.setFailCount(failCount);
        entity.setSummaryComment(summaryComment);
        entity.setSuggestedAction(failCount > 0
                ? "优先核对通知通道适配器、消息模板和责任人路由，再补发失败通道"
                : warningCount > 0
                ? "存在部分通道派发失败，建议核对失败通道配置并执行补发"
                : "继续保持派发/补发队列巡检");
        entity.setRecommendedRoute("/payment-issues");
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(TASK_CODE_ISSUE_ALERT_DISPATCH);
        result.setTaskName("异常告警派发");
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setWarningCount(warningCount);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        return result;
    }

    /**
     * 单条异常告警的多通道派发结果。
     */
    static class DispatchOutcome {
        private final int successCount;
        private final int failCount;
        private final int totalChannels;
        private final boolean noExternalDispatchRequired;

        DispatchOutcome(int successCount, int failCount, int totalChannels, boolean noExternalDispatchRequired) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.totalChannels = totalChannels;
            this.noExternalDispatchRequired = noExternalDispatchRequired;
        }

        boolean isAllSucceeded() {
            return noExternalDispatchRequired || (totalChannels > 0 && successCount == totalChannels);
        }

        boolean hasAnySuccess() {
            return noExternalDispatchRequired || successCount > 0;
        }

        static DispatchOutcome noExternalDispatch() {
            return new DispatchOutcome(0, 0, 0, true);
        }
    }
}
