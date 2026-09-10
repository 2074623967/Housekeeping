package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.common.RequestIdHolder;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付交易代理共享的调用鉴权、治理校验和最小审计边界。
 */
@Service
public class GatewayTransactionAccessService {

    static final String PAYMENT_SUBMIT = "PAYMENT_SUBMIT";
    static final String PAYMENT_QUERY = "PAYMENT_QUERY";
    static final String REFUND_SUBMIT = "REFUND_SUBMIT";
    static final String REFUND_QUERY = "REFUND_QUERY";

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayTransactionAccessService.class);
    private static final String ENABLED = "ENABLED";

    private final GatewayAccessMapper gatewayAccessMapper;
    private final GatewayTransactionProperties properties;

    public GatewayTransactionAccessService(GatewayAccessMapper gatewayAccessMapper,
                                           GatewayTransactionProperties properties) {
        this.gatewayAccessMapper = gatewayAccessMapper;
        this.properties = properties;
    }

    String newRequestId() {
        return RequestIdHolder.requestId();
    }

    AccessContext authorize(String appCode, String accessKey, String channelCode, String operationType) {
        String expectedAppCode = requireText(properties.getAppCode(), "GATEWAY_ACCESS_NOT_CONFIGURED",
                "gateway caller identity is not configured");
        String expectedAccessKey = requireText(properties.getAccessKey(), "GATEWAY_ACCESS_NOT_CONFIGURED",
                "gateway access credential is not configured");
        if (!expectedAppCode.equals(appCode) || !constantTimeEquals(expectedAccessKey, accessKey)) {
            throw new GatewayAccessException("GATEWAY_ACCESS_DENIED", "gateway access denied");
        }

        String normalizedChannelCode = requireText(channelCode, "GATEWAY_INVALID_ARGUMENT",
                "channelCode is required").toLowerCase(Locale.ROOT);
        GatewayTransactionProperties.Channel channel = properties.getChannels().get(normalizedChannelCode);
        if (channel == null) {
            throw new GatewayAccessException("GATEWAY_CHANNEL_NOT_CONFIGURED",
                    "payment transaction channel is not configured");
        }
        String gatewayCode = requireText(channel.getGatewayCode(), "GATEWAY_CHANNEL_NOT_CONFIGURED",
                "payment transaction channel mapping is not configured");
        String upstreamUrl = resolveUpstreamUrl(channel, operationType);
        GatewayChannelEntity gateway = validateGovernance(gatewayCode);
        return new AccessContext(expectedAppCode, normalizedChannelCode, gatewayCode,
                valueOrDefault(gateway.getSignAlgorithm(), "N/A"), upstreamUrl);
    }

    void audit(String requestId, AccessContext context, String fallbackAppCode, String clientIp,
               String operationType, String resultStatus, String resultStatusType, String riskHint) {
        GatewayAuditLogEntity auditLog = new GatewayAuditLogEntity();
        auditLog.setRequestId(requestId);
        auditLog.setAppCode(context == null
                ? valueOrDefault(fallbackAppCode, "UNKNOWN") : context.getAppCode());
        auditLog.setGatewayCode(context == null ? "UNRESOLVED" : context.getGatewayCode());
        auditLog.setOperationType(operationType);
        auditLog.setSignType(context == null ? "N/A" : context.getSignType());
        auditLog.setClientIp(valueOrDefault(clientIp, "UNKNOWN"));
        auditLog.setResultStatus(resultStatus);
        auditLog.setResultStatusType(resultStatusType);
        auditLog.setRiskHint(riskHint);
        try {
            gatewayAccessMapper.insertAuditLog(auditLog);
        } catch (RuntimeException exception) {
            LOGGER.error("Failed to persist gateway audit, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
        }
    }

    private String resolveUpstreamUrl(GatewayTransactionProperties.Channel channel, String operationType) {
        if (PAYMENT_SUBMIT.equals(operationType)) {
            return requireText(channel.getPaymentSubmitUrl(), "GATEWAY_UPSTREAM_NOT_CONFIGURED",
                    "payment submit upstream is not configured");
        }
        if (PAYMENT_QUERY.equals(operationType)) {
            return requireText(channel.getPaymentQueryUrl(), "GATEWAY_UPSTREAM_NOT_CONFIGURED",
                    "payment query upstream is not configured");
        }
        if (REFUND_QUERY.equals(operationType)) {
            return requireText(channel.getRefundQueryUrl(), "GATEWAY_UPSTREAM_NOT_CONFIGURED",
                    "refund query upstream is not configured");
        }
        if (REFUND_SUBMIT.equals(operationType)) {
            return requireText(channel.getRefundSubmitUrl(), "GATEWAY_UPSTREAM_NOT_CONFIGURED",
                    "refund submit upstream is not configured");
        }
        throw new GatewayAccessException("GATEWAY_OPERATION_NOT_SUPPORTED",
                "gateway transaction operation is not supported");
    }

    private GatewayChannelEntity validateGovernance(String gatewayCode) {
        GatewayAppEntity application = gatewayAccessMapper.findApplicationByCode(properties.getAppCode());
        if (application == null || !ENABLED.equalsIgnoreCase(application.getStatus())) {
            throw new GatewayAccessException("GATEWAY_APPLICATION_DISABLED", "gateway application is not enabled");
        }
        GatewayPermissionEntity permission = gatewayAccessMapper.findPermissionByCode(properties.getPermissionCode());
        if (permission == null || !properties.getAppCode().equals(permission.getAppCode())
                || !ENABLED.equalsIgnoreCase(permission.getStatus())) {
            throw new GatewayAccessException("GATEWAY_PERMISSION_DISABLED", "payment permission is not enabled");
        }
        GatewayChannelEntity gateway = gatewayAccessMapper.findGatewayByCode(gatewayCode);
        if (gateway == null || !ENABLED.equalsIgnoreCase(gateway.getStatus())) {
            throw new GatewayAccessException("GATEWAY_CHANNEL_DISABLED", "payment gateway is not enabled");
        }
        GatewayCertificateEntity certificate = gatewayAccessMapper.findEnabledCertificateByGatewayCode(gatewayCode);
        if (certificate == null || isExpired(certificate.getExpireAt())) {
            throw new GatewayAccessException("GATEWAY_CERTIFICATE_INVALID",
                    "payment gateway certificate is missing or expired");
        }
        GatewayReleaseRouteDTO route = gatewayAccessMapper.findEnabledReleaseRoute(
                gatewayCode, properties.getEnvironment());
        if (route == null || route.getTrafficPercent() == null || route.getTrafficPercent() <= 0) {
            throw new GatewayAccessException("GATEWAY_ROUTE_DISABLED", "payment release route is not enabled");
        }
        return gateway;
    }

    private boolean isExpired(String expireAt) {
        try {
            return !StringUtils.hasText(expireAt) || LocalDate.parse(expireAt.trim()).isBefore(LocalDate.now());
        } catch (DateTimeParseException exception) {
            return true;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new GatewayAccessException(code, message);
        }
        return value.trim();
    }

    private String valueOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    @Getter
    static class AccessContext {

        private final String appCode;
        private final String channelCode;
        private final String gatewayCode;
        private final String signType;
        private final String upstreamUrl;

        AccessContext(String appCode, String channelCode, String gatewayCode,
                      String signType, String upstreamUrl) {
            this.appCode = appCode;
            this.channelCode = channelCode;
            this.gatewayCode = gatewayCode;
            this.signType = signType;
            this.upstreamUrl = upstreamUrl;
        }
    }
}
