package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.common.RequestIdHolder;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.GatewayReleaseRouteDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentQueryResponseDTO;
import com.abc123.gatewayaccess.entity.GatewayAppEntity;
import com.abc123.gatewayaccess.entity.GatewayAuditLogEntity;
import com.abc123.gatewayaccess.entity.GatewayCertificateEntity;
import com.abc123.gatewayaccess.entity.GatewayChannelEntity;
import com.abc123.gatewayaccess.entity.GatewayPermissionEntity;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 校验接入治理状态后，将查单请求转发给环境显式配置的真实渠道集成服务。
 */
@Service
public class PaymentOrderQueryServiceImpl implements PaymentOrderQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOrderQueryServiceImpl.class);
    private static final String ENABLED = "ENABLED";

    private final GatewayAccessMapper gatewayAccessMapper;
    private final GatewayTransactionProperties properties;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentOrderQueryServiceImpl(GatewayAccessMapper gatewayAccessMapper,
                                        GatewayTransactionProperties properties,
                                        RestTemplateBuilder restTemplateBuilder) {
        this(gatewayAccessMapper, properties, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    PaymentOrderQueryServiceImpl(GatewayAccessMapper gatewayAccessMapper,
                                 GatewayTransactionProperties properties,
                                 RestTemplate restTemplate) {
        this.gatewayAccessMapper = gatewayAccessMapper;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                            PaymentOrderQueryRequestDTO request) {
        String requestId = RequestIdHolder.requestId();
        String gatewayCode = "UNRESOLVED";
        String signType = "N/A";
        try {
            validateRequest(request);
            authenticate(appCode, accessKey);
            GatewayTransactionProperties.Channel channel = resolveChannel(request.getChannelCode());
            gatewayCode = requireText(channel.getGatewayCode(), "GATEWAY_CHANNEL_NOT_CONFIGURED",
                    "payment query channel mapping is not configured");
            String queryUrl = requireText(channel.getPaymentQueryUrl(), "GATEWAY_UPSTREAM_NOT_CONFIGURED",
                    "payment query upstream is not configured");
            GatewayChannelEntity gateway = validateGovernance(gatewayCode);
            signType = valueOrDefault(gateway.getSignAlgorithm(), "N/A");

            UpstreamPaymentQueryResponseDTO response = restTemplate.postForObject(
                    queryUrl.trim(), request, UpstreamPaymentQueryResponseDTO.class);
            PaymentOrderQueryResultDTO result = validateUpstreamResponse(response);
            if (!StringUtils.hasText(result.getQuerySource())) {
                result.setQuerySource("GATEWAY_ACCESS_PROXY");
            } else {
                result.setQuerySource(result.getQuerySource().trim());
            }
            audit(requestId, properties.getAppCode(), gatewayCode, signType, clientIp,
                    "SUCCESS", "success", "渠道查单已由配置的真实上游返回确定状态");
            return result;
        } catch (GatewayAccessException exception) {
            audit(requestId, valueOrDefault(appCode, "UNKNOWN"), gatewayCode, signType, clientIp,
                    "FAILED", "danger", exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            audit(requestId, valueOrDefault(appCode, "UNKNOWN"), gatewayCode, signType, clientIp,
                    "FAILED", "danger", "GATEWAY_UPSTREAM_UNAVAILABLE");
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "payment query upstream is unavailable, please retry later");
        }
    }

    private void validateRequest(PaymentOrderQueryRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || !StringUtils.hasText(request.getOrderNo()) || !StringUtils.hasText(request.getChannelCode())) {
            throw new GatewayAccessException("GATEWAY_INVALID_ARGUMENT",
                    "paymentOrderId, orderNo and channelCode are required");
        }
        request.setPaymentOrderId(request.getPaymentOrderId().trim());
        request.setOrderNo(request.getOrderNo().trim());
        request.setChannelCode(request.getChannelCode().trim().toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(request.getChannelTransactionNo())) {
            request.setChannelTransactionNo(request.getChannelTransactionNo().trim());
        }
    }

    private void authenticate(String appCode, String accessKey) {
        String expectedAppCode = requireText(properties.getAppCode(), "GATEWAY_ACCESS_NOT_CONFIGURED",
                "gateway caller identity is not configured");
        String expectedAccessKey = requireText(properties.getAccessKey(), "GATEWAY_ACCESS_NOT_CONFIGURED",
                "gateway access credential is not configured");
        if (!expectedAppCode.equals(appCode) || !constantTimeEquals(expectedAccessKey, accessKey)) {
            throw new GatewayAccessException("GATEWAY_ACCESS_DENIED", "gateway access denied");
        }
    }

    private GatewayTransactionProperties.Channel resolveChannel(String channelCode) {
        GatewayTransactionProperties.Channel channel = properties.getChannels().get(channelCode);
        if (channel == null) {
            throw new GatewayAccessException("GATEWAY_CHANNEL_NOT_CONFIGURED",
                    "payment query channel is not configured");
        }
        return channel;
    }

    private GatewayChannelEntity validateGovernance(String gatewayCode) {
        GatewayAppEntity application = gatewayAccessMapper.findApplicationByCode(properties.getAppCode());
        if (application == null || !ENABLED.equalsIgnoreCase(application.getStatus())) {
            throw new GatewayAccessException("GATEWAY_APPLICATION_DISABLED", "gateway application is not enabled");
        }
        GatewayPermissionEntity permission = gatewayAccessMapper.findPermissionByCode(properties.getPermissionCode());
        if (permission == null || !properties.getAppCode().equals(permission.getAppCode())
                || !ENABLED.equalsIgnoreCase(permission.getStatus())) {
            throw new GatewayAccessException("GATEWAY_PERMISSION_DISABLED", "payment query permission is not enabled");
        }
        GatewayChannelEntity gateway = gatewayAccessMapper.findGatewayByCode(gatewayCode);
        if (gateway == null || !ENABLED.equalsIgnoreCase(gateway.getStatus())) {
            throw new GatewayAccessException("GATEWAY_CHANNEL_DISABLED", "payment query gateway is not enabled");
        }
        GatewayCertificateEntity certificate = gatewayAccessMapper.findEnabledCertificateByGatewayCode(gatewayCode);
        if (certificate == null || isExpired(certificate.getExpireAt())) {
            throw new GatewayAccessException("GATEWAY_CERTIFICATE_INVALID",
                    "payment query gateway certificate is missing or expired");
        }
        GatewayReleaseRouteDTO route = gatewayAccessMapper.findEnabledReleaseRoute(
                gatewayCode, properties.getEnvironment());
        if (route == null || route.getTrafficPercent() == null || route.getTrafficPercent() <= 0) {
            throw new GatewayAccessException("GATEWAY_ROUTE_DISABLED", "payment query release route is not enabled");
        }
        return gateway;
    }

    private PaymentOrderQueryResultDTO validateUpstreamResponse(UpstreamPaymentQueryResponseDTO response) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null
                || !StringUtils.hasText(response.getData().getTradeStatus())) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment query upstream returned no definitive trade status");
        }
        response.getData().setTradeStatus(response.getData().getTradeStatus().trim());
        if (StringUtils.hasText(response.getData().getChannelTransactionNo())) {
            response.getData().setChannelTransactionNo(response.getData().getChannelTransactionNo().trim());
        }
        return response.getData();
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

    private void audit(String requestId, String appCode, String gatewayCode, String signType, String clientIp,
                       String resultStatus, String resultStatusType, String riskHint) {
        GatewayAuditLogEntity auditLog = new GatewayAuditLogEntity();
        auditLog.setRequestId(requestId);
        auditLog.setAppCode(valueOrDefault(appCode, "UNKNOWN"));
        auditLog.setGatewayCode(valueOrDefault(gatewayCode, "UNRESOLVED"));
        auditLog.setOperationType("PAYMENT_QUERY");
        auditLog.setSignType(valueOrDefault(signType, "N/A"));
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
}
