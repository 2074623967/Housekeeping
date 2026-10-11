package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentQueryResponseDTO;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
    private static final Set<String> TRADE_STATUSES = new HashSet<>(Arrays.asList(
            "PREPAY_CREATED", "RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED",
            "WAIT_CALLBACK", "SUCCESS", "CLOSED", "FAIL"));
    private final GatewayTransactionAccessService accessService;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentOrderQueryServiceImpl(GatewayTransactionAccessService accessService,
                                        GatewayTransactionProperties properties,
                                        RestTemplateBuilder restTemplateBuilder) {
        this(accessService, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    PaymentOrderQueryServiceImpl(GatewayAccessMapper gatewayAccessMapper,
                                 GatewayTransactionProperties properties,
                                 RestTemplate restTemplate) {
        this(new GatewayTransactionAccessService(gatewayAccessMapper, properties), restTemplate);
    }

    PaymentOrderQueryServiceImpl(GatewayTransactionAccessService accessService, RestTemplate restTemplate) {
        this.accessService = accessService;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                            PaymentOrderQueryRequestDTO request) {
        String requestId = accessService.newRequestId();
        GatewayTransactionAccessService.AccessContext context = null;
        try {
            validateRequest(request);
            context = accessService.authorize(appCode, accessKey, request.getChannelCode(),
                    GatewayTransactionAccessService.PAYMENT_QUERY);
            request.setChannelCode(context.getChannelCode());
            UpstreamPaymentQueryResponseDTO response = restTemplate.postForObject(
                    context.getUpstreamUrl(), request, UpstreamPaymentQueryResponseDTO.class);
            PaymentOrderQueryResultDTO result = validateUpstreamResponse(response);
            if (!StringUtils.hasText(result.getQuerySource())) {
                result.setQuerySource("GATEWAY_ACCESS_PROXY");
            } else {
                result.setQuerySource(result.getQuerySource().trim());
            }
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "SUCCESS", "success", "渠道查单已由配置的真实上游返回确定状态");
            return result;
        } catch (GatewayAccessException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "FAILED", "danger", exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "FAILED", "danger", "GATEWAY_UPSTREAM_UNAVAILABLE");
            LOGGER.error("Payment query upstream failed, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "payment query upstream is unavailable, please retry later");
        }
    }

    private void validateRequest(PaymentOrderQueryRequestDTO request) {
        if (request == null) {
            throw new GatewayAccessException("GATEWAY_INVALID_ARGUMENT",
                    "payment query request is required");
        }
        request.setPaymentOrderId(required(request.getPaymentOrderId(), "paymentOrderId", 64));
        request.setOrderNo(required(request.getOrderNo(), "orderNo", 64));
        request.setChannelCode(required(request.getChannelCode(), "channelCode", 64)
                .toLowerCase(Locale.ROOT));
        request.setChannelTransactionNo(optional(request.getChannelTransactionNo(), "channelTransactionNo", 128));
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName + " is required");
        }
        return validateText(value, fieldName, maxLength).trim();
    }

    private String optional(String value, String fieldName, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = validateText(value, fieldName, maxLength).trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String validateText(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength || containsIsoControl(value)) {
            throw invalid(fieldName + " is invalid");
        }
        return value;
    }

    private boolean containsIsoControl(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private GatewayAccessException invalid(String message) {
        return new GatewayAccessException("GATEWAY_INVALID_ARGUMENT", message);
    }

    private PaymentOrderQueryResultDTO validateUpstreamResponse(UpstreamPaymentQueryResponseDTO response) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment query upstream returned no definitive trade status");
        }
        PaymentOrderQueryResultDTO result = response.getData();
        String tradeStatus = responseText(result.getTradeStatus(), 32);
        if (tradeStatus == null || !TRADE_STATUSES.contains(tradeStatus.toUpperCase(Locale.ROOT))) {
            throw invalidResponse();
        }
        result.setTradeStatus(tradeStatus.toUpperCase(Locale.ROOT));
        result.setChannelTransactionNo(responseText(result.getChannelTransactionNo(), 128));
        String querySource = responseText(result.getQuerySource(), 64);
        result.setQuerySource(querySource == null ? "GATEWAY_ACCESS_PROXY" : querySource);
        result.setChannelReturnCode(responseText(result.getChannelReturnCode(), 64));
        result.setStandardizedCode(responseText(result.getStandardizedCode(), 64));
        result.setStandardizedMessage(responseText(result.getStandardizedMessage(), 256));
        result.setHandlingSuggestion(responseText(result.getHandlingSuggestion(), 256));
        result.setRetryable(responseText(result.getRetryable(), 16));
        result.setManualInterventionRequired(responseText(result.getManualInterventionRequired(), 16));
        result.setMappingVersion(responseText(result.getMappingVersion(), 32));
        return result;
    }

    private String responseText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength || containsIsoControl(value)) {
            throw invalidResponse();
        }
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private GatewayAccessException invalidResponse() {
        return new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                "payment query upstream returned an invalid trade fact");
    }
}
