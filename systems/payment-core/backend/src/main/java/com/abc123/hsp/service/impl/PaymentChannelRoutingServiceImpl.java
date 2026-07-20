package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO;
import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRuntimeDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付渠道路由配置驱动实现。
 */
@Service
public class PaymentChannelRoutingServiceImpl implements PaymentChannelRoutingService {

    private final PaymentConfigMapper paymentConfigMapper;

    public PaymentChannelRoutingServiceImpl(PaymentConfigMapper paymentConfigMapper) {
        this.paymentConfigMapper = paymentConfigMapper;
    }

    @Override
    public PaymentRouteDecisionDTO resolve(PaymentRouteContextDTO routeContext) {
        if (routeContext == null || !StringUtils.hasText(routeContext.getPaymentMethod())) {
            throw new IllegalArgumentException("paymentMethod is required");
        }
        List<PaymentChannelRoutingConfigDTO> enabledChannels = paymentConfigMapper.findEnabledChannelsForRouting();
        if (enabledChannels.isEmpty()) {
            throw new IllegalArgumentException("no enabled payment channel found");
        }
        List<PaymentRouteRuleRuntimeDTO> enabledRules = paymentConfigMapper.findEnabledRouteRulesForRouting();
        PaymentRouteDecisionDTO decision = resolveByRules(routeContext, enabledChannels, enabledRules);
        if (decision != null) {
            return decision;
        }
        decision = resolveByRequestedChannel(routeContext, enabledChannels);
        if (decision != null) {
            return decision;
        }
        decision = resolveByPaymentMethod(routeContext, enabledChannels);
        if (decision != null) {
            return decision;
        }
        throw new IllegalArgumentException("unsupported payment channel");
    }

    private PaymentRouteDecisionDTO resolveByRules(PaymentRouteContextDTO routeContext,
                                                   List<PaymentChannelRoutingConfigDTO> enabledChannels,
                                                   List<PaymentRouteRuleRuntimeDTO> enabledRules) {
        for (PaymentRouteRuleRuntimeDTO rule : enabledRules) {
            if (!matchesScene(routeContext, rule.getMatchScene()) || !matchesExpression(routeContext, rule.getMatchExpression())) {
                continue;
            }
            PaymentChannelRoutingConfigDTO targetChannel = findEnabledChannel(enabledChannels, rule.getTargetChannelCode());
            if (targetChannel != null) {
                return buildDecision(targetChannel.getChannelCode(), rule.getRuleCode(), rule.getRuleName() + " -> " + targetChannel.getChannelCode());
            }
            PaymentChannelRoutingConfigDTO fallbackChannel = findEnabledChannel(enabledChannels, rule.getFallbackChannelCode());
            if (fallbackChannel != null) {
                return buildDecision(
                        fallbackChannel.getChannelCode(),
                        rule.getRuleCode() + "_FALLBACK",
                        rule.getRuleName() + " target disabled, fallback -> " + fallbackChannel.getChannelCode()
                );
            }
        }
        return null;
    }

    private PaymentRouteDecisionDTO resolveByRequestedChannel(PaymentRouteContextDTO routeContext,
                                                              List<PaymentChannelRoutingConfigDTO> enabledChannels) {
        String normalizedRequestedChannel = normalizeChannelCode(routeContext.getRequestedChannelCode());
        if (!StringUtils.hasText(normalizedRequestedChannel)) {
            return null;
        }
        PaymentChannelRoutingConfigDTO requestedChannel = findEnabledChannel(enabledChannels, normalizedRequestedChannel);
        if (requestedChannel == null) {
            return null;
        }
        return buildDecision(
                requestedChannel.getChannelCode(),
                "REQUESTED_CHANNEL",
                "requested channel direct -> " + requestedChannel.getChannelCode()
        );
    }

    private PaymentRouteDecisionDTO resolveByPaymentMethod(PaymentRouteContextDTO routeContext,
                                                           List<PaymentChannelRoutingConfigDTO> enabledChannels) {
        String normalizedMethod = normalizeText(routeContext.getPaymentMethod());
        for (PaymentChannelRoutingConfigDTO channel : enabledChannels) {
            if (normalizeText(channel.getPaymentMethod()).equals(normalizedMethod)) {
                return buildDecision(
                        channel.getChannelCode(),
                        "PAYMENT_METHOD_DEFAULT",
                        "payment method default -> " + channel.getChannelCode()
                );
            }
        }
        return null;
    }

    private boolean matchesScene(PaymentRouteContextDTO routeContext, String matchScene) {
        if (!StringUtils.hasText(matchScene)) {
            return true;
        }
        String normalizedPayScene = normalizeText(routeContext.getPayScene());
        String normalizedTerminal = normalizeText(routeContext.getTerminal());
        String normalizedCustomerType = resolveCustomerType(routeContext.getCustomerName());
        for (String token : matchScene.split("/")) {
            String normalizedToken = normalizeText(token);
            if (normalizedToken.equals(normalizedPayScene)
                    || normalizedToken.equals(normalizedTerminal)
                    || normalizedToken.equals(normalizedCustomerType)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesExpression(PaymentRouteContextDTO routeContext, String expression) {
        if (!StringUtils.hasText(expression)) {
            return true;
        }
        String[] orSegments = expression.split("(?i)\\s+OR\\s+");
        for (String orSegment : orSegments) {
            if (matchesAndSegment(routeContext, orSegment)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAndSegment(PaymentRouteContextDTO routeContext, String andSegment) {
        String[] andConditions = andSegment.split("(?i)\\s+AND\\s+");
        for (String condition : andConditions) {
            if (!matchesCondition(routeContext, condition.trim())) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(PaymentRouteContextDTO routeContext, String condition) {
        if (condition.contains(">=")) {
            return compareNumber(routeContext, condition, ">=");
        }
        if (condition.contains("<=")) {
            return compareNumber(routeContext, condition, "<=");
        }
        if (condition.contains(">")) {
            return compareNumber(routeContext, condition, ">");
        }
        if (condition.contains("<")) {
            return compareNumber(routeContext, condition, "<");
        }
        if (condition.contains("=")) {
            String[] parts = condition.split("=", 2);
            String key = normalizeText(parts[0]);
            String value = normalizeText(parts[1]);
            if ("paymentmethod".equals(key)) {
                return normalizeText(routeContext.getPaymentMethod()).equals(value);
            }
            if ("customertype".equals(key)) {
                return resolveCustomerType(routeContext.getCustomerName()).equals(value);
            }
            if ("requestedchannel".equals(key) || "channelcode".equals(key)) {
                return normalizeChannelCode(routeContext.getRequestedChannelCode()).equals(value);
            }
            if ("terminal".equals(key)) {
                return normalizeText(routeContext.getTerminal()).equals(value);
            }
            return false;
        }
        return false;
    }

    private boolean compareNumber(PaymentRouteContextDTO routeContext, String condition, String operator) {
        String[] parts = condition.split(java.util.regex.Pattern.quote(operator), 2);
        if (!"amount".equals(normalizeText(parts[0]))) {
            return false;
        }
        BigDecimal amount = routeContext.getAmount() == null ? BigDecimal.ZERO : routeContext.getAmount();
        BigDecimal compareValue = new BigDecimal(parts[1].trim());
        int compareResult = amount.compareTo(compareValue);
        switch (operator) {
            case ">=":
                return compareResult >= 0;
            case "<=":
                return compareResult <= 0;
            case ">":
                return compareResult > 0;
            case "<":
                return compareResult < 0;
            default:
                return false;
        }
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
}
