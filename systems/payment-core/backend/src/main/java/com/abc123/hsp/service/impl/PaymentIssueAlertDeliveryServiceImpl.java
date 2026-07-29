package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentIssueAlertDeliveryService;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE = "PAYMENT_ISSUE_ALERT_RECEIPT_RECONCILE";
    private static final String SOURCE_CHANNEL_OUTBOX = "IN_APP_OUTBOX";
    private static final String ROUTE_CHANNEL_IN_APP = "IN_APP";
    private static final DateTimeFormatter ALERT_LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern RETRY_COUNT_PATTERN = Pattern.compile("失败重试(\\d+)次");
    private static final Pattern RETRY_COOLDOWN_PATTERN = Pattern.compile("间隔(\\d+)分钟");
    private static final Pattern RETRY_BACKOFF_FACTOR_PATTERN = Pattern.compile("退避系数(\\d+)倍");
    private static final Pattern RETRY_MAX_COOLDOWN_PATTERN = Pattern.compile("最大间隔(\\d+)分钟");
    private static final Pattern RETRY_REPLAY_WINDOW_PATTERN = Pattern.compile("防重放窗口(\\d+)分钟");
    private static final Pattern RETRY_FRESHNESS_WINDOW_PATTERN = Pattern.compile("时间窗(\\d+)分钟");
    private static final Pattern RATE_LIMIT_PATTERN = Pattern.compile("每(?:(\\d+)分钟|分钟)\\s*(\\d+)\\s*条");
    private static final Pattern TEMPLATE_PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final int PROVIDER_CIRCUIT_BREAKER_WINDOW_MINUTES = 10;
    private static final int PROVIDER_CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;
    private static final List<String> SUCCESSFUL_PROVIDER_DELIVERY_STATUSES = Arrays.asList("ACCEPTED", "DELIVERED");

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final List<PaymentIssueAlertNotifier> notifiers;

    @Autowired
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

    @Override
    @Transactional
    public PaymentTaskActionResultDTO reconcileDeliveryReceipts() {
        return reconcileDeliveryReceiptsByMode(RUN_MODE_MANUAL, "payment-core-admin");
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO autoReconcileDeliveryReceipts() {
        return reconcileDeliveryReceiptsByMode(RUN_MODE_AUTO, "payment-issue-alert-receipt-scheduler");
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
            if (paymentTaskCenterMapper.hasSuccessfulIssueAlertChannelDelivery(item.getAlertNo(), channelCode)) {
                successCount++;
                continue;
            }
            List<PaymentAlertProviderConfigDTO> candidateProviders = selectProviderConfigs(
                    paymentTaskCenterMapper.findEnabledAlertProvidersByChannel(channelCode),
                    item
            );
            if (candidateProviders.isEmpty()) {
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                        item,
                        triggeredBy,
                        channelCode,
                        "派发失败",
                        "danger",
                        buildFailureResult("CONFIG_MISSING", "告警供应商配置缺失", item)
                ));
                failCount++;
                continue;
            }
            PaymentIssueAlertNotifier notifier = notifierMap.get(channelCode);
            if (notifier == null) {
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                        buildDeliveryItem(item, candidateProviders.get(0)),
                        triggeredBy,
                        channelCode,
                        "派发失败",
                        "danger",
                        buildFailureResult("NOTIFIER_MISSING", "未找到对应通知器实现", item)
                ));
                failCount++;
                continue;
            }
            if (dispatchByProviderFallback(item, triggeredBy, channelCode, notifier, candidateProviders)) {
                successCount++;
            } else {
                failCount++;
            }
        }
        return new DispatchOutcome(successCount, failCount, configuredChannels.size(), false);
    }

    /**
     * 针对同一通知通道依次尝试多个候选供应商，前一个失败时自动切换到下一个候选。
     */
    private boolean dispatchByProviderFallback(PaymentIssueAlertDispatchItemDTO item,
                                               String triggeredBy,
                                               String channelCode,
                                               PaymentIssueAlertNotifier notifier,
                                               List<PaymentAlertProviderConfigDTO> candidateProviders) {
        for (PaymentAlertProviderConfigDTO providerConfig : candidateProviders) {
            PaymentIssueAlertDispatchItemDTO deliveryItem = buildDeliveryItem(item, providerConfig);
            PaymentIssueAlertDeliveryResultDTO guardFailure = resolveDispatchGuardFailure(providerConfig, deliveryItem, channelCode);
            if (guardFailure != null) {
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                        deliveryItem,
                        triggeredBy,
                        channelCode,
                        "派发失败",
                        "danger",
                        guardFailure
                ));
                continue;
            }
            try {
                PaymentIssueAlertDeliveryResultDTO deliveryResult = notifier.send(deliveryItem);
                PaymentIssueAlertDeliveryResultDTO normalizedResult = normalizeDeliveryResult(
                        deliveryItem,
                        deliveryResult,
                        "ACCEPTED",
                        "供应商已受理告警"
                );
                if (!isSuccessfulProviderDeliveryStatus(normalizedResult.getProviderDeliveryStatus())) {
                    paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                            deliveryItem,
                            triggeredBy,
                            channelCode,
                            "派发失败",
                            "danger",
                            normalizedResult
                    ));
                    continue;
                }
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                        deliveryItem,
                        triggeredBy,
                        channelCode,
                        "已派发",
                        "success",
                        normalizedResult
                ));
                return true;
            } catch (RuntimeException ex) {
                PaymentIssueAlertDeliveryResultDTO failureResult = buildFailureResult("SEND_EXCEPTION", ex.getMessage(), deliveryItem);
                paymentTaskCenterMapper.insertIssueAlertLog(buildDeliveryLog(
                        deliveryItem,
                        triggeredBy,
                        channelCode,
                        "派发失败",
                        "danger",
                        failureResult
                ));
            }
        }
        return false;
    }

    /**
     * 根据供应商配置中的重试策略与限流策略控制补发频率，避免短时间内重复轰炸同一通道。
     */
    private PaymentIssueAlertDeliveryResultDTO resolveDispatchGuardFailure(PaymentAlertProviderConfigDTO providerConfig,
                                                                           PaymentIssueAlertDispatchItemDTO item,
                                                                           String channelCode) {
        PaymentIssueAlertDeliveryResultDTO circuitBreakerFailure = resolveProviderCircuitBreakerFailure(providerConfig, item, channelCode);
        if (circuitBreakerFailure != null) {
            return circuitBreakerFailure;
        }
        RetryGuard retryGuard = parseRetryGuard(providerConfig.getRetryPolicy());
        PaymentIssueAlertLogEntity latestLog = paymentTaskCenterMapper.findLatestIssueAlertChannelDeliveryLog(item.getAlertNo(), channelCode);
        PaymentIssueAlertDeliveryResultDTO replayGuardFailure = resolveReplayGuardFailure(retryGuard, latestLog, item);
        if (replayGuardFailure != null) {
            return replayGuardFailure;
        }
        if (!retryGuard.isEnabled()) {
            return resolveRateLimitGuardFailure(providerConfig, item, channelCode);
        }
        int failedAttemptCount = paymentTaskCenterMapper.countFailedIssueAlertChannelDeliveries(item.getAlertNo(), channelCode);
        if (retryGuard.hasRetryLimit() && failedAttemptCount > retryGuard.getRetryCount()) {
            return buildFailureResult("RETRY_LIMIT_REACHED", "已达到失败补发上限，等待人工介入处理", item);
        }
        if (!retryGuard.hasCooldownMinutes()) {
            return resolveRateLimitGuardFailure(providerConfig, item, channelCode);
        }
        if (latestLog == null || !"派发失败".equals(latestLog.getAlertStatus())) {
            return resolveRateLimitGuardFailure(providerConfig, item, channelCode);
        }
        LocalDateTime latestCreatedAt = parseAlertCreatedAt(latestLog.getCreatedAt());
        int effectiveCooldownMinutes = retryGuard.resolveEffectiveCooldownMinutes(failedAttemptCount);
        if (latestCreatedAt != null && effectiveCooldownMinutes > 0
                && LocalDateTime.now().isBefore(latestCreatedAt.plusMinutes(effectiveCooldownMinutes))) {
            return buildFailureResult(
                    "RETRY_COOLDOWN_ACTIVE",
                    String.format("补发冷却时间未到，请等待后再重试；当前要求间隔 %d 分钟", effectiveCooldownMinutes),
                    item
            );
        }
        return resolveRateLimitGuardFailure(providerConfig, item, channelCode);
    }

    private PaymentIssueAlertDeliveryResultDTO resolveReplayGuardFailure(RetryGuard retryGuard,
                                                                         PaymentIssueAlertLogEntity latestLog,
                                                                         PaymentIssueAlertDispatchItemDTO item) {
        if (latestLog == null || !retryGuard.hasReplayProtectionWindow()) {
            return null;
        }
        if (!isReplayProtectedDeliveryStatus(latestLog)) {
            return null;
        }
        LocalDateTime latestCreatedAt = parseAlertCreatedAt(latestLog.getCreatedAt());
        if (latestCreatedAt == null) {
            return null;
        }
        int protectionWindowMinutes = retryGuard.resolveReplayProtectionWindowMinutes();
        if (protectionWindowMinutes <= 0) {
            return null;
        }
        if (LocalDateTime.now().isAfter(latestCreatedAt.plusMinutes(protectionWindowMinutes))) {
            return null;
        }
        return buildFailureResult(
                "REPLAY_WINDOW_ACTIVE",
                String.format("命中服务端防重放窗口：最近一次成功派发仍在 %d 分钟保护期内", protectionWindowMinutes),
                item
        );
    }

    private boolean isReplayProtectedDeliveryStatus(PaymentIssueAlertLogEntity latestLog) {
        if ("已派发".equals(latestLog.getAlertStatus())) {
            return true;
        }
        return isSuccessfulProviderDeliveryStatus(latestLog.getProviderDeliveryStatus());
    }

    /**
     * 当某个供应商在短时间内连续失败过多时，先临时跳过它，交给后备供应商兜底。
     */
    private PaymentIssueAlertDeliveryResultDTO resolveProviderCircuitBreakerFailure(PaymentAlertProviderConfigDTO providerConfig,
                                                                                    PaymentIssueAlertDispatchItemDTO item,
                                                                                    String channelCode) {
        if (!StringUtils.hasText(providerConfig.getProviderCode())) {
            return null;
        }
        String sinceTime = LocalDateTime.now()
                .minusMinutes(PROVIDER_CIRCUIT_BREAKER_WINDOW_MINUTES)
                .format(ALERT_LOG_TIME_FORMATTER);
        int failedDeliveryCount = paymentTaskCenterMapper.countIssueAlertProviderFailedDeliveriesSince(
                providerConfig.getProviderCode(),
                channelCode,
                sinceTime
        );
        if (failedDeliveryCount < PROVIDER_CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
            return null;
        }
        return buildFailureResult(
                "PROVIDER_CIRCUIT_OPEN",
                String.format("供应商近 %d 分钟内失败 %d 次，已临时熔断并切换候选供应商", PROVIDER_CIRCUIT_BREAKER_WINDOW_MINUTES, failedDeliveryCount),
                item
        );
    }

    /**
     * 按供应商配置中的限流策略控制单位时间内的派发次数。
     */
    private PaymentIssueAlertDeliveryResultDTO resolveRateLimitGuardFailure(PaymentAlertProviderConfigDTO providerConfig,
                                                                            PaymentIssueAlertDispatchItemDTO item,
                                                                            String channelCode) {
        RateLimitGuard rateLimitGuard = parseRateLimitGuard(providerConfig.getRateLimitPolicy());
        if (!rateLimitGuard.isEnabled()) {
            return null;
        }
        String providerCode = providerConfig.getProviderCode();
        if (!StringUtils.hasText(providerCode)) {
            return null;
        }
        String sinceTime = LocalDateTime.now().minusMinutes(rateLimitGuard.getWindowMinutes()).format(ALERT_LOG_TIME_FORMATTER);
        int currentDeliveryCount = paymentTaskCenterMapper.countIssueAlertProviderDeliveriesSince(providerCode, channelCode, sinceTime);
        if (currentDeliveryCount >= rateLimitGuard.getThreshold()) {
            return buildFailureResult(
                    "RATE_LIMITED",
                    String.format("命中供应商限流策略：%d 分钟内最多 %d 条", rateLimitGuard.getWindowMinutes(), rateLimitGuard.getThreshold()),
                    item
            );
        }
        return null;
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
                                                        String alertStatusType,
                                                        PaymentIssueAlertDeliveryResultDTO deliveryResult) {
        PaymentIssueAlertLogEntity entity = buildBaseAlertLog(item, triggeredBy);
        entity.setAlertNo(buildDeliveryAlertLogNo(channelCode));
        entity.setSourceAlertNo(item.getAlertNo());
        entity.setAlertChannel(channelCode);
        entity.setAlertStatus(alertStatus);
        entity.setAlertStatusType(alertStatusType);
        entity.setAckStatus("无需回执");
        entity.setAckStatusType("info");
        entity.setProviderReceiptSnapshot(deliveryResult.getProviderReceiptSnapshot());
        entity.setProviderReceiptNo(deliveryResult.getProviderReceiptNo());
        entity.setProviderDeliveryStatus(deliveryResult.getProviderDeliveryStatus());
        entity.setProviderDeliveryMessage(deliveryResult.getProviderDeliveryMessage());
        entity.setRenderedContentSnapshot(deliveryResult.getRenderedContentSnapshot());
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
        entity.setAlertContent(buildAlertContent(item));
        entity.setProviderCode(item.getProviderCode());
        entity.setProviderName(item.getProviderName());
        entity.setEndpointAlias(item.getEndpointAlias());
        entity.setTemplateCode(item.getTemplateCode());
        entity.setTriggeredBy(triggeredBy);
        return entity;
    }

    private PaymentIssueAlertDeliveryResultDTO normalizeDeliveryResult(PaymentIssueAlertDispatchItemDTO item,
                                                                       PaymentIssueAlertDeliveryResultDTO deliveryResult,
                                                                       String defaultStatus,
                                                                       String defaultMessage) {
        PaymentIssueAlertDeliveryResultDTO normalizedResult = deliveryResult == null
                ? new PaymentIssueAlertDeliveryResultDTO()
                : deliveryResult;
        if (!StringUtils.hasText(normalizedResult.getProviderDeliveryStatus())) {
            normalizedResult.setProviderDeliveryStatus(defaultStatus);
        }
        if (!StringUtils.hasText(normalizedResult.getProviderDeliveryMessage())) {
            normalizedResult.setProviderDeliveryMessage(defaultMessage);
        }
        if (!StringUtils.hasText(normalizedResult.getProviderReceiptSnapshot())) {
            normalizedResult.setProviderReceiptSnapshot("LOCAL_RESULT:" + defaultStatus);
        }
        if (!StringUtils.hasText(normalizedResult.getRenderedContentSnapshot())) {
            normalizedResult.setRenderedContentSnapshot(buildAlertContent(item));
        }
        return normalizedResult;
    }

    private PaymentIssueAlertDeliveryResultDTO buildFailureResult(String status,
                                                                  String message,
                                                                  PaymentIssueAlertDispatchItemDTO item) {
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptSnapshot("LOCAL_FAILURE:" + status + ":" + (StringUtils.hasText(message) ? message : "未知异常"));
        result.setProviderDeliveryStatus(status);
        result.setProviderDeliveryMessage(StringUtils.hasText(message) ? message : "未知异常");
        result.setRenderedContentSnapshot(buildAlertContent(item));
        return result;
    }

    private boolean isSuccessfulProviderDeliveryStatus(String providerDeliveryStatus) {
        if (!StringUtils.hasText(providerDeliveryStatus)) {
            return false;
        }
        return SUCCESSFUL_PROVIDER_DELIVERY_STATUSES.stream()
                .anyMatch(status -> status.equalsIgnoreCase(providerDeliveryStatus.trim()));
    }

    private PaymentIssueAlertDispatchItemDTO buildDeliveryItem(PaymentIssueAlertDispatchItemDTO item,
                                                               PaymentAlertProviderConfigDTO providerConfig) {
        PaymentIssueAlertDispatchItemDTO deliveryItem = new PaymentIssueAlertDispatchItemDTO();
        deliveryItem.setAlertNo(item.getAlertNo());
        deliveryItem.setIssueNo(item.getIssueNo());
        deliveryItem.setPaymentOrderId(item.getPaymentOrderId());
        deliveryItem.setIssueType(item.getIssueType());
        deliveryItem.setSeverity(item.getSeverity());
        deliveryItem.setResponsibilityGroup(item.getResponsibilityGroup());
        deliveryItem.setReceiver(item.getReceiver());
        deliveryItem.setNotifyChannels(item.getNotifyChannels());
        deliveryItem.setEscalationLevel(item.getEscalationLevel());
        deliveryItem.setScheduleTag(item.getScheduleTag());
        deliveryItem.setAlertContent(item.getAlertContent());
        deliveryItem.setTriggeredBy(item.getTriggeredBy());
        deliveryItem.setProviderCode(providerConfig.getProviderCode());
        deliveryItem.setProviderName(providerConfig.getProviderName());
        deliveryItem.setEndpointAlias(providerConfig.getEndpointAlias());
        deliveryItem.setTemplateCode(providerConfig.getTemplateCode());
        deliveryItem.setTemplateBody(providerConfig.getTemplateBody());
        deliveryItem.setRenderedAlertContent(renderTemplate(providerConfig.getTemplateBody(), deliveryItem));
        return deliveryItem;
    }

    private String buildAlertContent(PaymentIssueAlertDispatchItemDTO item) {
        if (StringUtils.hasText(item.getRenderedAlertContent())) {
            return item.getRenderedAlertContent();
        }
        String alertContent = item.getAlertContent();
        if (!StringUtils.hasText(item.getProviderCode()) && !StringUtils.hasText(item.getTemplateCode())) {
            return alertContent;
        }
        StringBuilder builder = new StringBuilder(alertContent == null ? "" : alertContent);
        builder.append("【");
        if (StringUtils.hasText(item.getProviderName())) {
            builder.append("供应商：").append(item.getProviderName());
        } else if (StringUtils.hasText(item.getProviderCode())) {
            builder.append("供应商编码：").append(item.getProviderCode());
        }
        if (StringUtils.hasText(item.getTemplateCode())) {
            if (builder.charAt(builder.length() - 1) != '【') {
                builder.append("，");
            }
            builder.append("模板：").append(item.getTemplateCode());
        }
        if (StringUtils.hasText(item.getEndpointAlias())) {
            if (builder.charAt(builder.length() - 1) != '【') {
                builder.append("，");
            }
            builder.append("端点：").append(item.getEndpointAlias());
        }
        builder.append("】");
        return builder.toString();
    }

    private List<PaymentAlertProviderConfigDTO> selectProviderConfigs(List<PaymentAlertProviderConfigDTO> providerConfigs,
                                                                      PaymentIssueAlertDispatchItemDTO item) {
        List<PaymentAlertProviderConfigDTO> matchedProviders = new ArrayList<PaymentAlertProviderConfigDTO>();
        if (providerConfigs == null || providerConfigs.isEmpty()) {
            return matchedProviders;
        }
        for (PaymentAlertProviderConfigDTO providerConfig : providerConfigs) {
            if (matchesRouteRule(providerConfig.getRouteRule(), item)) {
                matchedProviders.add(providerConfig);
            }
        }
        return matchedProviders;
    }

    private boolean matchesRouteRule(String routeRule, PaymentIssueAlertDispatchItemDTO item) {
        if (!StringUtils.hasText(routeRule) || "DEFAULT".equalsIgnoreCase(routeRule.trim())) {
            return true;
        }
        String[] ruleSegments = routeRule.split("&");
        for (String ruleSegment : ruleSegments) {
            if (!matchesSingleRouteRule(ruleSegment, item)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesSingleRouteRule(String ruleSegment, PaymentIssueAlertDispatchItemDTO item) {
        if (!StringUtils.hasText(ruleSegment)) {
            return true;
        }
        String[] ruleParts = ruleSegment.split("=");
        if (ruleParts.length != 2) {
            return false;
        }
        String ruleKey = ruleParts[0].trim();
        String ruleValue = ruleParts[1].trim();
        String actualValue = resolveRouteRuleFieldValue(ruleKey, item);
        return StringUtils.hasText(actualValue) && ruleValue.equalsIgnoreCase(actualValue);
    }

    private String resolveRouteRuleFieldValue(String ruleKey, PaymentIssueAlertDispatchItemDTO item) {
        if ("severity".equalsIgnoreCase(ruleKey)) {
            return item.getSeverity();
        }
        if ("issueType".equalsIgnoreCase(ruleKey)) {
            return item.getIssueType();
        }
        if ("responsibilityGroup".equalsIgnoreCase(ruleKey)) {
            return item.getResponsibilityGroup();
        }
        if ("scheduleTag".equalsIgnoreCase(ruleKey)) {
            return item.getScheduleTag();
        }
        if ("receiver".equalsIgnoreCase(ruleKey)) {
            return item.getReceiver();
        }
        if ("escalationLevel".equalsIgnoreCase(ruleKey)) {
            return item.getEscalationLevel();
        }
        if ("triggeredBy".equalsIgnoreCase(ruleKey)) {
            return item.getTriggeredBy();
        }
        return null;
    }

    private String renderTemplate(String templateBody, PaymentIssueAlertDispatchItemDTO item) {
        if (!StringUtils.hasText(templateBody)) {
            return buildAlertContent(item);
        }
        Map<String, String> variables = buildTemplateVariables(item);
        Matcher matcher = TEMPLATE_PLACEHOLDER_PATTERN.matcher(templateBody);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = resolveTemplateVariableValue(variables, variableName);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private Map<String, String> buildTemplateVariables(PaymentIssueAlertDispatchItemDTO item) {
        Map<String, String> variables = new HashMap<String, String>();
        variables.put("alertNo", safeText(item.getAlertNo()));
        variables.put("issueNo", safeText(item.getIssueNo()));
        variables.put("paymentOrderId", safeText(item.getPaymentOrderId()));
        variables.put("issueType", safeText(item.getIssueType()));
        variables.put("severity", safeText(item.getSeverity()));
        variables.put("responsibilityGroup", safeText(item.getResponsibilityGroup()));
        variables.put("receiver", safeText(item.getReceiver()));
        variables.put("notifyChannels", safeText(item.getNotifyChannels()));
        variables.put("escalationLevel", safeText(item.getEscalationLevel()));
        variables.put("scheduleTag", safeText(item.getScheduleTag()));
        variables.put("providerCode", safeText(item.getProviderCode()));
        variables.put("providerName", safeText(item.getProviderName()));
        variables.put("endpointAlias", safeText(item.getEndpointAlias()));
        variables.put("templateCode", safeText(item.getTemplateCode()));
        variables.put("templateBody", safeText(item.getTemplateBody()));
        variables.put("alertContent", safeText(item.getAlertContent()));
        variables.put("triggeredBy", safeText(item.getTriggeredBy()));
        return variables;
    }

    private String resolveTemplateVariableValue(Map<String, String> variables, String variableName) {
        if (!StringUtils.hasText(variableName)) {
            return "-";
        }
        String value = variables.get(variableName.trim());
        return StringUtils.hasText(value) ? value : "-";
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private RetryGuard parseRetryGuard(String retryPolicy) {
        RetryGuard retryGuard = new RetryGuard();
        if (!StringUtils.hasText(retryPolicy)) {
            return retryGuard;
        }
        Matcher retryCountMatcher = RETRY_COUNT_PATTERN.matcher(retryPolicy);
        if (retryCountMatcher.find()) {
            retryGuard.setRetryCount(parseInteger(retryCountMatcher.group(1)));
        }
        Matcher cooldownMatcher = RETRY_COOLDOWN_PATTERN.matcher(retryPolicy);
        if (cooldownMatcher.find()) {
            retryGuard.setCooldownMinutes(parseInteger(cooldownMatcher.group(1)));
        }
        Matcher backoffFactorMatcher = RETRY_BACKOFF_FACTOR_PATTERN.matcher(retryPolicy);
        if (backoffFactorMatcher.find()) {
            retryGuard.setBackoffFactor(parseInteger(backoffFactorMatcher.group(1)));
        }
        Matcher maxCooldownMatcher = RETRY_MAX_COOLDOWN_PATTERN.matcher(retryPolicy);
        if (maxCooldownMatcher.find()) {
            retryGuard.setMaxCooldownMinutes(parseInteger(maxCooldownMatcher.group(1)));
        }
        Matcher replayWindowMatcher = RETRY_REPLAY_WINDOW_PATTERN.matcher(retryPolicy);
        if (replayWindowMatcher.find()) {
            retryGuard.setReplayWindowMinutes(parseInteger(replayWindowMatcher.group(1)));
        }
        Matcher freshnessWindowMatcher = RETRY_FRESHNESS_WINDOW_PATTERN.matcher(retryPolicy);
        if (freshnessWindowMatcher.find()) {
            retryGuard.setFreshnessWindowMinutes(parseInteger(freshnessWindowMatcher.group(1)));
        }
        return retryGuard;
    }

    private RateLimitGuard parseRateLimitGuard(String rateLimitPolicy) {
        RateLimitGuard rateLimitGuard = new RateLimitGuard();
        if (!StringUtils.hasText(rateLimitPolicy)) {
            return rateLimitGuard;
        }
        Matcher matcher = RATE_LIMIT_PATTERN.matcher(rateLimitPolicy.replaceAll("\\s+", ""));
        if (!matcher.find()) {
            return rateLimitGuard;
        }
        String windowText = matcher.group(1);
        Integer windowMinutes = parseInteger(windowText);
        rateLimitGuard.setWindowMinutes(windowMinutes == null ? Integer.valueOf(1) : windowMinutes);
        rateLimitGuard.setThreshold(parseInteger(matcher.group(2)));
        return rateLimitGuard;
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime parseAlertCreatedAt(String createdAt) {
        if (!StringUtils.hasText(createdAt)) {
            return null;
        }
        try {
            return LocalDateTime.parse(createdAt.trim(), ALERT_LOG_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
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

    private PaymentTaskActionResultDTO reconcileDeliveryReceiptsByMode(String runMode, String triggeredBy) {
        List<PaymentIssueAlertLogEntity> acceptedLogs = paymentTaskCenterMapper.findAcceptedIssueAlertDeliveryLogs();
        int successCount = 0;
        int failCount = 0;
        for (PaymentIssueAlertLogEntity acceptedLog : acceptedLogs) {
            PaymentIssueAlertLogEntity receiptUpdate = buildReceiptUpdate(acceptedLog, triggeredBy);
            int affectedRows = paymentTaskCenterMapper.updateIssueAlertProviderReceipt(receiptUpdate);
            if (affectedRows > 0) {
                successCount++;
            } else {
                failCount++;
            }
        }
        return buildReceiptReconcileResult(
                runMode,
                triggeredBy,
                acceptedLogs.size(),
                successCount,
                failCount,
                buildReceiptReconcileSummary(acceptedLogs.size(), successCount, failCount)
        );
    }

    private PaymentIssueAlertLogEntity buildReceiptUpdate(PaymentIssueAlertLogEntity acceptedLog, String triggeredBy) {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo(acceptedLog.getAlertNo());
        entity.setProviderDeliveryStatus("DELIVERED");
        entity.setProviderDeliveryMessage("供应商回执回查成功，消息已送达");
        entity.setAckStatus("已确认");
        entity.setAckStatusType("success");
        entity.setTriggeredBy(triggeredBy);
        return entity;
    }

    private String buildReceiptReconcileSummary(int processedCount, int successCount, int failCount) {
        if (processedCount == 0) {
            return "当前没有供应商已受理但未确认送达的异常告警。";
        }
        return String.format("已回查 %d 条供应商告警回执，确认送达 %d 条，回写失败 %d 条。", processedCount, successCount, failCount);
    }

    private PaymentTaskActionResultDTO buildReceiptReconcileResult(String runMode,
                                                                   String triggeredBy,
                                                                   int processedCount,
                                                                   int successCount,
                                                                   int failCount,
                                                                   String summaryComment) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo("TL" + System.currentTimeMillis());
        entity.setTaskCode(TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE);
        entity.setTaskName("异常告警回执回查");
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
        entity.setSuggestedAction(failCount > 0
                ? "优先核对供应商回执接口、告警日志并发更新和通知通道状态"
                : "继续保持供应商回执回查任务自动巡检");
        entity.setRecommendedRoute("/payment-issues");
        entity.setTriggeredBy(triggeredBy);
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(TASK_CODE_ISSUE_ALERT_RECEIPT_RECONCILE);
        result.setTaskName("异常告警回执回查");
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setWarningCount(0);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        return result;
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

    /**
     * 告警补发护栏配置，来源于供应商配置中的重试策略。
     */
    static class RetryGuard {
        private Integer retryCount;
        private Integer cooldownMinutes;
        private Integer backoffFactor;
        private Integer maxCooldownMinutes;
        private Integer replayWindowMinutes;
        private Integer freshnessWindowMinutes;

        boolean isEnabled() {
            return hasRetryLimit() || hasCooldownMinutes() || hasReplayProtectionWindow();
        }

        boolean hasRetryLimit() {
            return retryCount != null && retryCount.intValue() >= 0;
        }

        boolean hasCooldownMinutes() {
            return cooldownMinutes != null && cooldownMinutes.intValue() > 0;
        }

        Integer getRetryCount() {
            return retryCount;
        }

        void setRetryCount(Integer retryCount) {
            this.retryCount = retryCount;
        }

        Integer getCooldownMinutes() {
            return cooldownMinutes;
        }

        void setCooldownMinutes(Integer cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
        }

        Integer getBackoffFactor() {
            return backoffFactor;
        }

        void setBackoffFactor(Integer backoffFactor) {
            this.backoffFactor = backoffFactor;
        }

        Integer getMaxCooldownMinutes() {
            return maxCooldownMinutes;
        }

        void setMaxCooldownMinutes(Integer maxCooldownMinutes) {
            this.maxCooldownMinutes = maxCooldownMinutes;
        }

        Integer getReplayWindowMinutes() {
            return replayWindowMinutes;
        }

        void setReplayWindowMinutes(Integer replayWindowMinutes) {
            this.replayWindowMinutes = replayWindowMinutes;
        }

        Integer getFreshnessWindowMinutes() {
            return freshnessWindowMinutes;
        }

        void setFreshnessWindowMinutes(Integer freshnessWindowMinutes) {
            this.freshnessWindowMinutes = freshnessWindowMinutes;
        }

        boolean hasReplayProtectionWindow() {
            return replayWindowMinutes != null && replayWindowMinutes.intValue() > 0
                    || freshnessWindowMinutes != null && freshnessWindowMinutes.intValue() > 0;
        }

        int resolveReplayProtectionWindowMinutes() {
            int replayWindow = replayWindowMinutes == null ? 0 : replayWindowMinutes.intValue();
            int freshnessWindow = freshnessWindowMinutes == null ? 0 : freshnessWindowMinutes.intValue();
            return Math.max(replayWindow, freshnessWindow);
        }

        int resolveEffectiveCooldownMinutes(int failedAttemptCount) {
            if (!hasCooldownMinutes()) {
                return 0;
            }
            int effectiveCooldown = cooldownMinutes.intValue();
            int normalizedBackoffFactor = backoffFactor == null || backoffFactor.intValue() < 2
                    ? 1
                    : backoffFactor.intValue();
            int retryIndex = Math.max(failedAttemptCount - 1, 0);
            for (int i = 0; i < retryIndex; i++) {
                effectiveCooldown = effectiveCooldown * normalizedBackoffFactor;
                if (maxCooldownMinutes != null && maxCooldownMinutes.intValue() > 0
                        && effectiveCooldown >= maxCooldownMinutes.intValue()) {
                    return maxCooldownMinutes.intValue();
                }
            }
            if (maxCooldownMinutes != null && maxCooldownMinutes.intValue() > 0) {
                return Math.min(effectiveCooldown, maxCooldownMinutes.intValue());
            }
            return effectiveCooldown;
        }
    }

    /**
     * 告警通道级限流护栏配置，来源于供应商配置中的限流策略。
     */
    static class RateLimitGuard {
        private Integer windowMinutes;
        private Integer threshold;

        boolean isEnabled() {
            return windowMinutes != null && windowMinutes.intValue() > 0
                    && threshold != null && threshold.intValue() > 0;
        }

        Integer getWindowMinutes() {
            return windowMinutes;
        }

        void setWindowMinutes(Integer windowMinutes) {
            this.windowMinutes = windowMinutes;
        }

        Integer getThreshold() {
            return threshold;
        }

        void setThreshold(Integer threshold) {
            this.threshold = threshold;
        }
    }
}
