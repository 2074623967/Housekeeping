package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.RefundOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamRefundQueryResponseDTO;
import com.abc123.gatewayaccess.service.RefundOrderQueryService;
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
 * 校验接入治理状态后，将退款查单转发给环境显式配置的渠道集成服务。
 */
@Service
public class RefundOrderQueryServiceImpl implements RefundOrderQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RefundOrderQueryServiceImpl.class);
    private static final Set<String> REFUND_STATUSES = new HashSet<>(
            Arrays.asList("PROCESSING", "SUCCESS", "FAIL"));

    private final GatewayTransactionAccessService accessService;
    private final RestTemplate restTemplate;

    @Autowired
    public RefundOrderQueryServiceImpl(GatewayTransactionAccessService accessService,
                                       GatewayTransactionProperties properties,
                                       RestTemplateBuilder restTemplateBuilder) {
        this(accessService, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    RefundOrderQueryServiceImpl(GatewayTransactionAccessService accessService, RestTemplate restTemplate) {
        this.accessService = accessService;
        this.restTemplate = restTemplate;
    }

    @Override
    public RefundOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                           RefundOrderQueryRequestDTO request) {
        String requestId = accessService.newRequestId();
        GatewayTransactionAccessService.AccessContext context = null;
        try {
            validateRequest(request);
            context = accessService.authorize(appCode, accessKey, request.getChannelCode(),
                    GatewayTransactionAccessService.REFUND_QUERY);
            request.setChannelCode(context.getChannelCode());
            UpstreamRefundQueryResponseDTO response = restTemplate.postForObject(
                    context.getUpstreamUrl(), request, UpstreamRefundQueryResponseDTO.class);
            RefundOrderQueryResultDTO result = validateUpstreamResponse(response, request.getChannelRefundNo());
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_QUERY,
                    "SUCCESS", "success", "退款查单已由配置的真实上游返回确定状态");
            return result;
        } catch (GatewayAccessException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_QUERY,
                    "FAILED", "danger", exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.REFUND_QUERY,
                    "FAILED", "danger", "GATEWAY_UPSTREAM_UNAVAILABLE");
            LOGGER.error("Refund query upstream failed, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "refund query upstream is unavailable, please retry later");
        }
    }

    private void validateRequest(RefundOrderQueryRequestDTO request) {
        if (request == null) {
            throw invalid("refund query request is required");
        }
        request.setRefundOrderId(required(request.getRefundOrderId(), "refundOrderId", 64));
        request.setRefundAttemptNo(required(request.getRefundAttemptNo(), "refundAttemptNo", 64));
        request.setChannelCode(required(request.getChannelCode(), "channelCode", 64)
                .toLowerCase(Locale.ROOT));
        request.setChannelRefundNo(optional(request.getChannelRefundNo(), "channelRefundNo", 128));
    }

    private RefundOrderQueryResultDTO validateUpstreamResponse(UpstreamRefundQueryResponseDTO response,
                                                                String expectedChannelRefundNo) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
            throw invalidResponse("refund query upstream returned no definitive result");
        }
        RefundOrderQueryResultDTO result = response.getData();
        result.setRefundStatus(requiredResponse(result.getRefundStatus(), "refundStatus", 32)
                .toUpperCase(Locale.ROOT));
        result.setChannelRefundNo(requiredResponse(result.getChannelRefundNo(), "channelRefundNo", 128));
        if (!REFUND_STATUSES.contains(result.getRefundStatus())) {
            throw invalidResponse("refund query upstream returned an unsupported refundStatus");
        }
        if (StringUtils.hasText(expectedChannelRefundNo)
                && !expectedChannelRefundNo.equals(result.getChannelRefundNo())) {
            throw invalidResponse("refund query upstream returned a mismatched channelRefundNo");
        }
        if (!StringUtils.hasText(result.getQuerySource())) {
            result.setQuerySource("GATEWAY_ACCESS_PROXY");
        } else {
            result.setQuerySource(validateText(result.getQuerySource(), "querySource", 64, false));
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
        if (value == null) {
            return null;
        }
        if (containsIsoControl(value)) {
            throw invalid(fieldName + " is invalid");
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return validateText(value, fieldName, maxLength, true);
    }

    private String requiredResponse(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw invalidResponse("refund query upstream omitted " + fieldName);
        }
        return validateText(value, fieldName, maxLength, false);
    }

    private String validateText(String value, String fieldName, int maxLength, boolean requestField) {
        String normalized = value.trim();
        if (normalized.length() > maxLength || containsIsoControl(value)) {
            if (requestField) {
                throw invalid(fieldName + " is invalid");
            }
            throw invalidResponse("refund query upstream returned invalid " + fieldName);
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
