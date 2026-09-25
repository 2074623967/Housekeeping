package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.abc123.hsp.service.RefundChannelSubmitService;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 非 demo 环境的 gateway-access 退款提交实现。
 */
@Service
@Profile("!demo")
public class GatewayAccessRefundChannelSubmitServiceImpl implements RefundChannelSubmitService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final Set<String> STATUSES = new HashSet<>(Arrays.asList("PROCESSING", "SUCCESS"));
    private static final Set<String> STATUS_TYPES = new HashSet<>(
            Arrays.asList("info", "warn", "danger", "success"));
    private static final Set<String> REJECTED_BEFORE_UPSTREAM = new HashSet<>(Arrays.asList(
            "GATEWAY_ACCESS_NOT_CONFIGURED",
            "GATEWAY_ACCESS_DENIED",
            "GATEWAY_INVALID_ARGUMENT",
            "GATEWAY_CHANNEL_NOT_CONFIGURED",
            "GATEWAY_APPLICATION_DISABLED",
            "GATEWAY_PERMISSION_DISABLED",
            "GATEWAY_CHANNEL_DISABLED",
            "GATEWAY_CERTIFICATE_INVALID",
            "GATEWAY_ROUTE_DISABLED",
            "GATEWAY_UPSTREAM_NOT_CONFIGURED",
            "GATEWAY_OPERATION_NOT_SUPPORTED"));

    private final boolean enabled;
    private final String submitUrl;
    private final Set<String> supportedChannels;
    private final String appCode;
    private final String accessKey;
    private final RestTemplate restTemplate;

    @Autowired
    public GatewayAccessRefundChannelSubmitServiceImpl(
            @Value("${payment.refund-submit.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.refund-submit.gateway-access.submit-url:}") String submitUrl,
            @Value("${payment.refund-submit.gateway-access.supported-channels:wx_h5,alipay_h5,offline_bank}")
                    String supportedChannels,
            @Value("${payment.refund-submit.gateway-access.app-code:APP_PAY_CORE}") String appCode,
            @Value("${payment.refund-submit.gateway-access.access-key:}") String accessKey,
            @Value("${payment.refund-submit.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, submitUrl, supportedChannels, appCode, accessKey,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    GatewayAccessRefundChannelSubmitServiceImpl(
            boolean enabled, String submitUrl, String supportedChannels,
            String appCode, String accessKey, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.submitUrl = submitUrl;
        this.supportedChannels = parseChannels(supportedChannels);
        this.appCode = appCode;
        this.accessKey = accessKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public RefundChannelSubmitResultDTO submit(RefundChannelSubmitRequestDTO request) {
        try {
            GatewayRefundSubmitRequest normalizedRequest = validateAndNormalizeRequest(request);
            if (!isConfigured(normalizedRequest.getChannelCode())) {
                throw submitFailed("gateway-access refund submit is disabled or not configured", false);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-App-Code", appCode);
            headers.set("X-Gateway-Access-Key", accessKey);
            ResponseEntity<GatewayRefundSubmitResponse> responseEntity = restTemplate.exchange(
                    submitUrl.trim(), HttpMethod.POST, new HttpEntity<>(normalizedRequest, headers),
                    GatewayRefundSubmitResponse.class);
            GatewayRefundSubmitResponse response = responseEntity.getBody();
            if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
                boolean resultUnknown = response == null
                        || !REJECTED_BEFORE_UPSTREAM.contains(response.getCode());
                throw submitFailed("gateway-access rejected refund submit", resultUnknown);
            }
            RefundChannelSubmitResultDTO result = response.getData();
            normalizeAndValidate(result);
            result.setResponsePayload(buildResponsePayload(normalizedRequest.getChannelCode(), result));
            return result;
        } catch (RefundChannelSubmitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw RefundChannelSubmitException.sanitized(
                    "gateway-access refund submit failed; query before retrying", true);
        }
    }

    private GatewayRefundSubmitRequest validateAndNormalizeRequest(RefundChannelSubmitRequestDTO request) {
        if (request == null) {
            throw invalidRequest();
        }
        GatewayRefundSubmitRequest normalized = new GatewayRefundSubmitRequest();
        normalized.setRefundOrderId(requiredRequest(request.getRefundOrderId(), 64));
        normalized.setRefundAttemptNo(requiredRequest(request.getRefundAttemptNo(), 64));
        normalized.setPaymentOrderId(requiredRequest(request.getPaymentOrderId(), 64));
        normalized.setOrderNo(requiredRequest(request.getOrderNo(), 64));
        normalized.setCustomerName(optionalRequest(request.getCustomerName(), 128));
        normalized.setRefundMethod(requiredRequest(request.getRefundMethod(), 32));
        normalized.setRefundReason(optionalRequest(request.getRefundReason(), 255));
        normalized.setChannelCode(requiredRequest(request.getChannelCode(), 64));
        BigDecimal amount = request.getRefundAmount();
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw invalidRequest();
        }
        normalized.setRefundAmount(amount);
        return normalized;
    }

    private String requiredRequest(String value, int maxLength) {
        String normalized = optionalRequest(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private String optionalRequest(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (containsIsoControl(value)) {
            throw invalidRequest();
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw invalidRequest();
        }
        String normalized = value.trim();
        return normalized;
    }

    private boolean isConfigured(String channelCode) {
        return enabled
                && StringUtils.hasText(submitUrl)
                && StringUtils.hasText(appCode)
                && StringUtils.hasText(accessKey)
                && supportedChannels.contains(channelCode.toLowerCase(Locale.ROOT));
    }

    private void normalizeAndValidate(RefundChannelSubmitResultDTO result) {
        result.setChannelRefundNo(required(result.getChannelRefundNo(), "channel refund number", 128));
        result.setStatus(required(result.getStatus(), "status", 32).toUpperCase(Locale.ROOT));
        result.setStatusType(required(result.getStatusType(), "status type", 32).toLowerCase(Locale.ROOT));
        if (!STATUSES.contains(result.getStatus()) || !STATUS_TYPES.contains(result.getStatusType())) {
            throw submitFailed("gateway-access returned an unsupported refund acceptance status", true);
        }
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw submitFailed("gateway-access returned no " + fieldName, true);
        }
        if (containsIsoControl(value)) {
            throw submitFailed("gateway-access returned an invalid " + fieldName, true);
        }
        if (value.length() > maxLength) {
            throw submitFailed("gateway-access returned an invalid " + fieldName, true);
        }
        String normalized = value.trim();
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

    private String buildResponsePayload(String channelCode, RefundChannelSubmitResultDTO result) {
        return "{\"source\":\"gateway-access\",\"channelCode\":\""
                + escapeJson(channelCode) + "\",\"channelRefundNo\":\""
                + escapeJson(result.getChannelRefundNo()) + "\"}";
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Set<String> parseChannels(String channels) {
        if (!StringUtils.hasText(channels)) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        Arrays.stream(channels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private RefundChannelSubmitException submitFailed(String message, boolean resultUnknown) {
        return new RefundChannelSubmitException(message, resultUnknown);
    }

    private RefundChannelSubmitException invalidRequest() {
        return submitFailed("gateway-access refund submit request is invalid", false);
    }

    @Data
    public static class GatewayRefundSubmitRequest {

        private String refundOrderId;
        private String refundAttemptNo;
        private String paymentOrderId;
        private String orderNo;
        private String customerName;
        private BigDecimal refundAmount;
        private String refundMethod;
        private String refundReason;
        private String channelCode;
    }

    @Data
    public static class GatewayRefundSubmitResponse {

        private String code;
        private String message;
        private RefundChannelSubmitResultDTO data;
    }
}
