package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamRefundSubmitResponseDTO;
import com.abc123.gatewayaccess.service.RefundOrderSubmitService;
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
 * 校验接入治理状态后，将退款提交转发给环境显式配置的渠道集成服务。
 */
@Service
public class RefundOrderSubmitServiceImpl implements RefundOrderSubmitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundOrderSubmitServiceImpl.class);
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList("PROCESSING", "SUCCESS"));
    private static final Set<String> STATUS_TYPES = new HashSet<>(
            Arrays.asList("info", "warn", "danger", "success"));
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");

    private final GatewayTransactionAccessService accessService;
    private final RestTemplate restTemplate;

    @Autowired
    public RefundOrderSubmitServiceImpl(GatewayTransactionAccessService accessService,
                                        GatewayTransactionProperties properties,
                                        RestTemplateBuilder restTemplateBuilder) {
        this(accessService, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    RefundOrderSubmitServiceImpl(GatewayTransactionAccessService accessService, RestTemplate restTemplate) {
        this.accessService = accessService;
        this.restTemplate = restTemplate;
    }

    @Override
    public RefundOrderSubmitResultDTO submit(String appCode, String accessKey, String clientIp,
                                             RefundOrderSubmitRequestDTO request) {
        String requestId = accessService.newRequestId();
        GatewayTransactionAccessService.AccessContext context = null;
        boolean upstreamInvoked = false;
        try {
            validateRequest(request);
            context = accessService.authorize(appCode, accessKey, request.getChannelCode(),
                    GatewayTransactionAccessService.REFUND_SUBMIT);
            request.setChannelCode(context.getChannelCode());
            upstreamInvoked = true;
            UpstreamRefundSubmitResponseDTO response = restTemplate.postForObject(
                    context.getUpstreamUrl(), request, UpstreamRefundSubmitResponseDTO.class);
            RefundOrderSubmitResultDTO result = validateUpstreamResponse(response);
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_SUBMIT,
                    "SUCCESS", "success", "退款请求已由配置的真实上游明确受理");
            return result;
        } catch (GatewayAccessException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_SUBMIT,
                    upstreamInvoked ? "UNKNOWN" : "FAILED",
                    upstreamInvoked ? "warn" : "danger",
                    exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_SUBMIT,
                    upstreamInvoked ? "UNKNOWN" : "FAILED",
                    upstreamInvoked ? "warn" : "danger",
                    "GATEWAY_UPSTREAM_UNAVAILABLE");
            LOGGER.error("Refund submit upstream failed, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "refund submit upstream is unavailable; query before retrying");
        }
    }

    private void validateRequest(RefundOrderSubmitRequestDTO request) {
        if (request == null) {
            throw invalid("refund submit request is required");
        }
        request.setRefundOrderId(required(request.getRefundOrderId(), "refundOrderId", 64));
        request.setPaymentOrderId(required(request.getPaymentOrderId(), "paymentOrderId", 64));
        request.setOrderNo(required(request.getOrderNo(), "orderNo", 64));
        request.setCustomerName(optional(request.getCustomerName(), "customerName", 128));
        request.setRefundMethod(required(request.getRefundMethod(), "refundMethod", 64));
        request.setRefundReason(optional(request.getRefundReason(), "refundReason", 255));
        request.setChannelCode(required(request.getChannelCode(), "channelCode", 64)
                .toLowerCase(Locale.ROOT));
        if (request.getRefundAmount() == null
                || request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getRefundAmount().compareTo(MAX_AMOUNT) > 0
                || request.getRefundAmount().stripTrailingZeros().scale() > 2) {
            throw invalid("refundAmount is invalid");
        }
    }

    private RefundOrderSubmitResultDTO validateUpstreamResponse(UpstreamRefundSubmitResponseDTO response) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
            throw invalidResponse("refund submit upstream returned no definitive acceptance");
        }
        RefundOrderSubmitResultDTO result = response.getData();
        result.setChannelRefundNo(requiredResponse(result.getChannelRefundNo(), "channelRefundNo", 128));
        result.setStatus(requiredResponse(result.getStatus(), "status", 32).toUpperCase(Locale.ROOT));
        result.setStatusType(requiredResponse(result.getStatusType(), "statusType", 32)
                .toLowerCase(Locale.ROOT));
        if (!STATUSES.contains(result.getStatus()) || !STATUS_TYPES.contains(result.getStatusType())) {
            throw invalidResponse("refund submit upstream returned an unsupported acceptance status");
        }
        return result;
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalid(fieldName + " is required");
        }
        return validateText(value, fieldName, maxLength, true);
    }

    private String optional(String value, String fieldName, int maxLength) {
        return StringUtils.hasText(value) ? validateText(value, fieldName, maxLength, true) : null;
    }

    private String requiredResponse(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalidResponse("refund submit upstream omitted " + fieldName);
        }
        return validateText(value, fieldName, maxLength, false);
    }

    private String validateText(String value, String fieldName, int maxLength, boolean requestField) {
        String normalized = value.trim();
        if (normalized.length() > maxLength || containsIsoControl(value)) {
            if (requestField) {
                throw invalid(fieldName + " is invalid");
            }
            throw invalidResponse("refund submit upstream returned invalid " + fieldName);
        }
        return normalized;
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

    private GatewayAccessException invalidResponse(String message) {
        return new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE", message);
    }
}
