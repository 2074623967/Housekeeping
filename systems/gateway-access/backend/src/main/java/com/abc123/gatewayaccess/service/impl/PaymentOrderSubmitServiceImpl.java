package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentSubmitResponseDTO;
import com.abc123.gatewayaccess.service.PaymentOrderSubmitService;
import java.math.BigDecimal;
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
 * 校验接入治理状态后，将支付提交转发给环境显式配置的真实渠道集成服务。
 */
@Service
public class PaymentOrderSubmitServiceImpl implements PaymentOrderSubmitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOrderSubmitServiceImpl.class);
    private static final Set<String> STATUS_TYPES = new HashSet<>(
            Arrays.asList("info", "warn", "danger", "success"));
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private final GatewayTransactionAccessService accessService;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentOrderSubmitServiceImpl(GatewayTransactionAccessService accessService,
                                         GatewayTransactionProperties properties,
                                         RestTemplateBuilder restTemplateBuilder) {
        this(accessService, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    PaymentOrderSubmitServiceImpl(GatewayTransactionAccessService accessService, RestTemplate restTemplate) {
        this.accessService = accessService;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOrderSubmitResultDTO submit(String appCode, String accessKey, String clientIp,
                                              PaymentOrderSubmitRequestDTO request) {
        String requestId = accessService.newRequestId();
        GatewayTransactionAccessService.AccessContext context = null;
        boolean upstreamInvoked = false;
        try {
            validateRequest(request);
            context = accessService.authorize(appCode, accessKey, request.getResolvedChannelCode(),
                    GatewayTransactionAccessService.PAYMENT_SUBMIT);
            request.setResolvedChannelCode(context.getChannelCode());
            upstreamInvoked = true;
            UpstreamPaymentSubmitResponseDTO response = restTemplate.postForObject(
                    context.getUpstreamUrl(), request, UpstreamPaymentSubmitResponseDTO.class);
            PaymentOrderSubmitResultDTO result = validateUpstreamResponse(response);
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_SUBMIT,
                    "SUCCESS", "success", "支付请求已由配置的真实上游明确受理");
            return result;
        } catch (GatewayAccessException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_SUBMIT,
                    upstreamInvoked ? "UNKNOWN" : "FAILED",
                    upstreamInvoked ? "warn" : "danger",
                    exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_SUBMIT,
                    upstreamInvoked ? "UNKNOWN" : "FAILED",
                    upstreamInvoked ? "warn" : "danger",
                    "GATEWAY_UPSTREAM_UNAVAILABLE");
            LOGGER.error("Payment submit upstream failed, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "payment submit upstream is unavailable; query before retrying");
        }
    }

    private void validateRequest(PaymentOrderSubmitRequestDTO request) {
        if (request == null) {
            throw invalid("payment submit request is required");
        }
        request.setPaymentOrderId(required(request.getPaymentOrderId(), "paymentOrderId", 64));
        request.setPrepayOrderNo(required(request.getPrepayOrderNo(), "prepayOrderNo", 64));
        request.setOrderNo(required(request.getOrderNo(), "orderNo", 64));
        request.setPayScene(required(request.getPayScene(), "payScene", 32));
        request.setPaymentMethod(required(request.getPaymentMethod(), "paymentMethod", 64));
        request.setSourceAppId(required(request.getSourceAppId(), "sourceAppId", 64));
        request.setResolvedChannelCode(required(
                request.getResolvedChannelCode(), "resolvedChannelCode", 64).toLowerCase(Locale.ROOT));
        request.setTerminal(required(request.getTerminal(), "terminal", 32));
        request.setClientIp(required(request.getClientIp(), "clientIp", 64));
        request.setIdempotencyKey(required(request.getIdempotencyKey(), "idempotencyKey", 128));
        request.setCustomerName(optional(request.getCustomerName(), "customerName", 128));
        request.setRequestedChannelCode(optional(
                request.getRequestedChannelCode(), "requestedChannelCode", 64));
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAmount().stripTrailingZeros().scale() > 2
                || request.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw invalid("amount must be a positive value with at most 16 integer digits and 2 decimals");
        }
    }

    private PaymentOrderSubmitResultDTO validateUpstreamResponse(UpstreamPaymentSubmitResponseDTO response) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment submit upstream returned no definitive acceptance result");
        }
        PaymentOrderSubmitResultDTO result = response.getData();
        result.setChannelTransactionNo(requiredResponse(
                result.getChannelTransactionNo(), "channelTransactionNo", 128));
        result.setAttemptStatus(requiredResponse(result.getAttemptStatus(), "attemptStatus", 32));
        result.setAttemptStatusType(requiredResponse(
                result.getAttemptStatusType(), "attemptStatusType", 16).toLowerCase(Locale.ROOT));
        result.setChannelReturnCode(requiredResponse(
                result.getChannelReturnCode(), "channelReturnCode", 64));
        if (!STATUS_TYPES.contains(result.getAttemptStatusType())) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment submit upstream returned an invalid attemptStatusType");
        }
        return result;
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName + " is required");
        }
        return validateText(value.trim(), fieldName, maxLength, true);
    }

    private String optional(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return validateText(value.trim(), fieldName, maxLength, true);
    }

    private String requiredResponse(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment submit upstream omitted " + fieldName);
        }
        return validateText(value.trim(), fieldName, maxLength, false);
    }

    private String validateText(String value, String fieldName, int maxLength, boolean requestField) {
        if (value.length() > maxLength || containsIsoControl(value)) {
            if (requestField) {
                throw invalid(fieldName + " is invalid");
            }
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment submit upstream returned invalid " + fieldName);
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
}
