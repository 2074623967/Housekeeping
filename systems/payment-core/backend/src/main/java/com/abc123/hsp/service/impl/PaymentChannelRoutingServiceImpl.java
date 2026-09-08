package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryHealthDTO;
import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付渠道路由配置驱动实现。
 */
@Service
public class PaymentChannelRoutingServiceImpl implements PaymentChannelRoutingService {

    private final PaymentConfigMapper paymentConfigMapper;
    private final PaymentMapper paymentMapper;
    private final boolean routeHealthEnabled;
    private final int queryHealthLookbackHours;
    private final int queryHealthFailureCountThreshold;
    private final int queryHealthFailureRateThresholdPercent;
    private final int queryHealthMinimumSampleCount;

    @Autowired
    public PaymentChannelRoutingServiceImpl(PaymentConfigMapper paymentConfigMapper,
                                            PaymentMapper paymentMapper,
                                            @Value("${payment.channel-query.monitor.route-health-enabled:false}")
                                                    boolean routeHealthEnabled,
                                            @Value("${payment.channel-query.monitor.lookback-hours:24}")
                                                    int queryHealthLookbackHours,
                                            @Value("${payment.channel-query.monitor.failure-count-threshold:5}")
                                                    int queryHealthFailureCountThreshold,
                                            @Value("${payment.channel-query.monitor.failure-rate-threshold-percent:30}")
                                                    int queryHealthFailureRateThresholdPercent,
                                            @Value("${payment.channel-query.monitor.failure-rate-minimum-sample-count:10}")
                                                    int queryHealthMinimumSampleCount) {
        this.paymentConfigMapper = paymentConfigMapper;
        this.paymentMapper = paymentMapper;
        this.routeHealthEnabled = routeHealthEnabled;
        this.queryHealthLookbackHours = Math.max(queryHealthLookbackHours, 1);
        this.queryHealthFailureCountThreshold = Math.max(queryHealthFailureCountThreshold, 1);
        this.queryHealthFailureRateThresholdPercent = Math.min(
                Math.max(queryHealthFailureRateThresholdPercent, 1), 100);
        this.queryHealthMinimumSampleCount = Math.max(queryHealthMinimumSampleCount, 1);
    }

    PaymentChannelRoutingServiceImpl(PaymentConfigMapper paymentConfigMapper,
                                     PaymentMapper paymentMapper) {
        this(paymentConfigMapper, paymentMapper, false, 24, 5, 30, 10);
    }

    @Override
    public PaymentRouteDecisionDTO resolve(PaymentRouteContextDTO routeContext) {
        if (routeContext == null || !StringUtils.hasText(routeContext.getPaymentMethod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "paymentMethod is required");
        }
        List<PaymentChannelRoutingConfigDTO> enabledChannels = paymentConfigMapper.findEnabledChannelsForRouting();
        if (enabledChannels.isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE, "no enabled payment channel found");
        }
        Map<String, PaymentChannelQueryHealthDTO> channelHealth = loadChannelHealth();
        List<PaymentRouteRuleRuntimeDTO> enabledRules = paymentConfigMapper.findEnabledRouteRulesForRouting();
        PaymentRouteDecisionDTO decision = resolveByRules(routeContext, enabledChannels, enabledRules, channelHealth);
        if (decision != null) {
            return decision;
        }
        decision = resolveByRequestedChannel(routeContext, enabledChannels, channelHealth);
        if (decision != null) {
            return decision;
        }
        decision = resolveByPaymentMethod(routeContext, enabledChannels, channelHealth);
        if (decision != null) {
            return decision;
        }
        throw new BusinessException(ErrorCode.PAYMENT_ROUTE_UNSUPPORTED, "unsupported payment channel");
    }

    private PaymentRouteDecisionDTO resolveByRules(PaymentRouteContextDTO routeContext,
                                                   List<PaymentChannelRoutingConfigDTO> enabledChannels,
                                                   List<PaymentRouteRuleRuntimeDTO> enabledRules,
                                                   Map<String, PaymentChannelQueryHealthDTO> channelHealth) {
        for (PaymentRouteRuleRuntimeDTO rule : enabledRules) {
            if (!matchesScene(routeContext, rule.getMatchScene()) || !matchesExpression(routeContext, rule.getMatchExpression())) {
                continue;
            }
            PaymentChannelRoutingConfigDTO targetChannel = findEnabledChannel(enabledChannels, rule.getTargetChannelCode());
            if (isRouteAvailable(routeContext, targetChannel, channelHealth)) {
                return buildDecision(targetChannel.getChannelCode(), rule.getRuleCode(), rule.getRuleName() + " -> " + targetChannel.getChannelCode());
            }
            PaymentChannelRoutingConfigDTO fallbackChannel = findEnabledChannel(enabledChannels, rule.getFallbackChannelCode());
            if (isRouteAvailable(routeContext, fallbackChannel, channelHealth)) {
                boolean targetHealthBlocked = targetChannel != null && !isChannelHealthy(targetChannel, channelHealth);
                return buildDecision(
                        fallbackChannel.getChannelCode(),
                        rule.getRuleCode() + (targetHealthBlocked ? "_HEALTH_FALLBACK" : "_FALLBACK"),
                        targetHealthBlocked
                                ? rule.getRuleName() + " 目标渠道短窗健康熔断，兜底 -> " + fallbackChannel.getChannelCode()
                                : rule.getRuleName() + " 目标渠道不可用，兜底 -> " + fallbackChannel.getChannelCode()
                );
            }
        }
        return null;
    }

    private PaymentRouteDecisionDTO resolveByRequestedChannel(PaymentRouteContextDTO routeContext,
                                                              List<PaymentChannelRoutingConfigDTO> enabledChannels,
                                                              Map<String, PaymentChannelQueryHealthDTO> channelHealth) {
        String normalizedRequestedChannel = normalizeChannelCode(routeContext.getRequestedChannelCode());
        if (!StringUtils.hasText(normalizedRequestedChannel)) {
            return null;
        }
        PaymentChannelRoutingConfigDTO requestedChannel = findEnabledChannel(enabledChannels, normalizedRequestedChannel);
        if (requestedChannel == null) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUESTED_CHANNEL_INVALID, "requested channel is disabled or missing");
        }
        if (!matchesChannelScene(routeContext, requestedChannel)) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_SCENE_MISMATCH, "requested channel does not match pay scene");
        }
        if (!isChannelHealthy(requestedChannel, channelHealth)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_UNAVAILABLE,
                    "requested channel is temporarily unavailable due to query health circuit breaker");
        }
        if (!matchesChannelDailyLimit(routeContext, requestedChannel)) {
            throw new BusinessException(ErrorCode.PAYMENT_CHANNEL_DAILY_LIMIT_EXCEEDED, "requested channel exceeds daily limit");
        }
        return buildDecision(
                requestedChannel.getChannelCode(),
                "REQUESTED_CHANNEL",
                "requested channel direct -> " + requestedChannel.getChannelCode()
        );
    }

    private PaymentRouteDecisionDTO resolveByPaymentMethod(PaymentRouteContextDTO routeContext,
                                                           List<PaymentChannelRoutingConfigDTO> enabledChannels,
                                                           Map<String, PaymentChannelQueryHealthDTO> channelHealth) {
        String normalizedMethod = normalizeText(routeContext.getPaymentMethod());
        List<PaymentChannelRoutingConfigDTO> candidates = new ArrayList<>();
        for (PaymentChannelRoutingConfigDTO channel : enabledChannels) {
            if (normalizeText(channel.getPaymentMethod()).equals(normalizedMethod)
                    && routeWeight(channel) > 0
                    && isRouteAvailable(routeContext, channel, channelHealth)) {
                candidates.add(channel);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        PaymentChannelRoutingConfigDTO selectedChannel = selectWeightedChannel(routeContext, candidates);
        if (selectedChannel == null) {
            return null;
        }
        if (candidates.size() == 1 || !StringUtils.hasText(routeContext.getRoutingKey())) {
            return buildDecision(
                    selectedChannel.getChannelCode(),
                    "PAYMENT_METHOD_DEFAULT",
                    "payment method default -> " + selectedChannel.getChannelCode()
            );
        }
        return buildDecision(
                selectedChannel.getChannelCode(),
                "PAYMENT_METHOD_WEIGHTED",
                "payment method weighted " + routeWeight(selectedChannel) + "/" + totalWeight(candidates)
                        + " -> " + selectedChannel.getChannelCode()
        );
    }

    private PaymentChannelRoutingConfigDTO selectWeightedChannel(PaymentRouteContextDTO routeContext,
                                                                  List<PaymentChannelRoutingConfigDTO> candidates) {
        if (candidates.size() == 1 || !StringUtils.hasText(routeContext.getRoutingKey())) {
            return candidates.get(0);
        }
        long totalWeight = totalWeight(candidates);
        if (totalWeight <= 0L) {
            return null;
        }
        CRC32 crc32 = new CRC32();
        crc32.update(routeContext.getRoutingKey().trim().getBytes(StandardCharsets.UTF_8));
        long bucket = crc32.getValue() % totalWeight;
        long cumulativeWeight = 0L;
        for (PaymentChannelRoutingConfigDTO candidate : candidates) {
            cumulativeWeight += routeWeight(candidate);
            if (bucket < cumulativeWeight) {
                return candidate;
            }
        }
        return null;
    }

    private long totalWeight(List<PaymentChannelRoutingConfigDTO> candidates) {
        long total = 0L;
        for (PaymentChannelRoutingConfigDTO candidate : candidates) {
            total += routeWeight(candidate);
        }
        return total;
    }

    private int routeWeight(PaymentChannelRoutingConfigDTO channel) {
        return channel.getRouteWeight() == null ? 100 : Math.max(channel.getRouteWeight(), 0);
    }

    private boolean isRouteAvailable(PaymentRouteContextDTO routeContext,
                                     PaymentChannelRoutingConfigDTO channel,
                                     Map<String, PaymentChannelQueryHealthDTO> channelHealth) {
        return channel != null
                && matchesChannelScene(routeContext, channel)
                && isChannelHealthy(channel, channelHealth)
                && matchesChannelDailyLimit(routeContext, channel);
    }

    private Map<String, PaymentChannelQueryHealthDTO> loadChannelHealth() {
        if (!routeHealthEnabled) {
            return Collections.emptyMap();
        }
        Map<String, PaymentChannelQueryHealthDTO> channelHealth = new HashMap<>();
        for (PaymentChannelQueryHealthDTO metric : paymentMapper.findRecentChannelQueryHealth(queryHealthLookbackHours)) {
            String channelCode = normalizeChannelCode(metric.getChannelCode());
            PaymentChannelQueryHealthDTO aggregatedMetric = channelHealth.get(channelCode);
            if (aggregatedMetric == null) {
                aggregatedMetric = new PaymentChannelQueryHealthDTO();
                aggregatedMetric.setChannelCode(channelCode);
                aggregatedMetric.setQueryCount(0);
                aggregatedMetric.setFailureCount(0);
                channelHealth.put(channelCode, aggregatedMetric);
            }
            aggregatedMetric.setQueryCount(
                    valueOrZero(aggregatedMetric.getQueryCount()) + valueOrZero(metric.getQueryCount()));
            aggregatedMetric.setFailureCount(
                    valueOrZero(aggregatedMetric.getFailureCount()) + valueOrZero(metric.getFailureCount()));
        }
        return channelHealth;
    }

    private boolean isChannelHealthy(PaymentChannelRoutingConfigDTO channel,
                                     Map<String, PaymentChannelQueryHealthDTO> channelHealth) {
        if (!routeHealthEnabled || channel == null) {
            return true;
        }
        PaymentChannelQueryHealthDTO metric = channelHealth.get(normalizeChannelCode(channel.getChannelCode()));
        if (metric == null) {
            return true;
        }
        int queryCount = valueOrZero(metric.getQueryCount());
        int failureCount = valueOrZero(metric.getFailureCount());
        return failureCount < queryHealthFailureCountThreshold
                && (queryCount < queryHealthMinimumSampleCount
                || failureCount * 100L < queryCount * (long) queryHealthFailureRateThresholdPercent);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean matchesChannelScene(PaymentRouteContextDTO routeContext, PaymentChannelRoutingConfigDTO channel) {
        return matchesScene(routeContext, channel.getSceneScope());
    }

    private boolean matchesChannelDailyLimit(PaymentRouteContextDTO routeContext, PaymentChannelRoutingConfigDTO channel) {
        if (channel == null || channel.getDailyLimit() == null || channel.getDailyLimit().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal todayAcceptedAmount = paymentMapper.sumTodayAcceptedAmountByChannel(channel.getChannelCode());
        BigDecimal acceptedAmount = todayAcceptedAmount == null ? BigDecimal.ZERO : todayAcceptedAmount;
        BigDecimal requestAmount = routeContext.getAmount() == null ? BigDecimal.ZERO : routeContext.getAmount();
        return acceptedAmount.add(requestAmount).compareTo(channel.getDailyLimit()) <= 0;
    }

    private boolean matchesScene(PaymentRouteContextDTO routeContext, String matchScene) {
        if (!StringUtils.hasText(matchScene)) {
            return true;
        }
        String normalizedPayScene = normalizeText(routeContext.getPayScene());
        String normalizedTerminal = normalizeTerminal(routeContext.getTerminal());
        String normalizedCustomerType = resolveCustomerType(routeContext.getCustomerName());
        for (String token : matchScene.split("/")) {
            String normalizedToken = normalizeSceneToken(token);
            if (normalizedToken.equals(normalizedPayScene)
                    || normalizedToken.equals(normalizedTerminal)
                    || normalizedToken.equals(normalizedCustomerType)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExpression(PaymentRouteContextDTO routeContext, String expression) {
        return PaymentRouteExpressionEvaluator.matches(routeContext, expression);
    }

    private PaymentChannelRoutingConfigDTO findEnabledChannel(List<PaymentChannelRoutingConfigDTO> enabledChannels, String channelCode) {
        String normalizedChannelCode = normalizeChannelCode(channelCode);
        for (PaymentChannelRoutingConfigDTO channel : enabledChannels) {
            if (normalizeChannelCode(channel.getChannelCode()).equals(normalizedChannelCode)) {
                return channel;
            }
        }
        return null;
    }

    private PaymentRouteDecisionDTO buildDecision(String channelCode, String routeRule, String routeResult) {
        PaymentRouteDecisionDTO decision = new PaymentRouteDecisionDTO();
        decision.setChannelCode(channelCode);
        decision.setRouteRule(routeRule);
        decision.setRouteResult(routeResult);
        return decision;
    }

    private String resolveCustomerType(String customerName) {
        String normalizedCustomerName = normalizeText(customerName);
        return normalizedCustomerName.contains("企业") ? "enterprise" : "personal";
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeChannelCode(String channelCode) {
        String normalizedChannel = normalizeText(channelCode);
        if (!StringUtils.hasText(normalizedChannel)) {
            return "";
        }
        if (normalizedChannel.contains("wx") || normalizedChannel.contains("wechat")) {
            return "wx_h5";
        }
        if (normalizedChannel.contains("ali")) {
            return "alipay_h5";
        }
        if (normalizedChannel.contains("bank")) {
            return "offline_bank";
        }
        return normalizedChannel;
    }

    private String normalizeTerminal(String terminal) {
        return normalizeSceneToken(terminal);
    }

    private String normalizeSceneToken(String token) {
        String normalizedToken = normalizeText(token);
        if ("mobile_web".equals(normalizedToken) || "mobile".equals(normalizedToken) || "h5".equals(normalizedToken)) {
            return "h5";
        }
        if ("pc_web".equals(normalizedToken) || "pc".equals(normalizedToken) || "web".equals(normalizedToken)) {
            return "pc";
        }
        if ("app".equals(normalizedToken) || "app_native".equals(normalizedToken)) {
            return "app";
        }
        return normalizedToken;
    }
}
