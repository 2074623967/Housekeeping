package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * gateway-access HTTP 支付提交适配器。
 */
@Component
@Order(1)
public class GatewayAccessPaymentChannelSubmitAdapter implements PaymentChannelSubmitAdapter {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final Set<String> STATUS_TYPES = new HashSet<>(
            Arrays.asList("info", "warn", "danger", "success"));
    private static final Set<String> NATIVE_PAYMENT_PARAM_KEYS = Collections.unmodifiableSet(
            new java.util.LinkedHashSet<>(Arrays.asList(
                    "timeStamp", "nonceStr", "package", "signType", "paySign")));

    private final boolean enabled;
    private final String submitUrl;
    private final Set<String> supportedChannels;
    private final String appCode;
    private final String accessKey;
    private final RestTemplate restTemplate;

    @Autowired
    public GatewayAccessPaymentChannelSubmitAdapter(
            @Value("${payment.submit.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.submit.gateway-access.submit-url:}") String submitUrl,
            @Value("${payment.submit.gateway-access.supported-channels:wx_h5,alipay_h5,offline_bank}")
                    String supportedChannels,
            @Value("${payment.submit.gateway-access.app-code:APP_PAY_CORE}") String appCode,
            @Value("${payment.submit.gateway-access.access-key:}") String accessKey,
            @Value("${payment.submit.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, submitUrl, supportedChannels, appCode, accessKey,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    GatewayAccessPaymentChannelSubmitAdapter(boolean enabled, String submitUrl, String supportedChannels,
                                             String appCode, String accessKey, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.submitUrl = submitUrl;
        this.supportedChannels = parseChannels(supportedChannels);
        this.appCode = appCode;
        this.accessKey = accessKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(String channelCode) {
        return enabled
                && StringUtils.hasText(submitUrl)
                && StringUtils.hasText(appCode)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(channelCode)
                && supportedChannels.contains(channelCode.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        try {
            PaymentChannelSubmitRequestDTO normalizedRequest = validateAndNormalizeRequest(request);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-App-Code", appCode);
            headers.set("X-Gateway-Access-Key", accessKey);
            ResponseEntity<GatewaySubmitResponse> responseEntity = restTemplate.exchange(
                    submitUrl.trim(), HttpMethod.POST, new HttpEntity<>(normalizedRequest, headers),
                    GatewaySubmitResponse.class);
            GatewaySubmitResponse response = responseEntity.getBody();
            if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
                throw submitFailed("gateway-access rejected payment submit");
            }
            PaymentChannelSubmitResultDTO result = response.getData();
            normalizeAndValidate(result, normalizedRequest);
            result.setResponsePayload(buildResponsePayload(normalizedRequest.getResolvedChannelCode(), result));
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw submitFailed("gateway-access payment submit failed; query before retrying");
        }
    }

    private PaymentChannelSubmitRequestDTO validateAndNormalizeRequest(PaymentChannelSubmitRequestDTO request) {
        if (request == null) {
            throw invalidRequest();
        }
        PaymentChannelSubmitRequestDTO normalized = new PaymentChannelSubmitRequestDTO();
        normalized.setPaymentOrderId(requiredRequest(request.getPaymentOrderId(), 64));
        normalized.setPrepayOrderNo(requiredRequest(request.getPrepayOrderNo(), 64));
        normalized.setOrderNo(requiredRequest(request.getOrderNo(), 64));
        normalized.setPayScene(requiredRequest(request.getPayScene(), 32));
        normalized.setCustomerName(optionalRequest(request.getCustomerName(), 128));
        normalized.setPaymentMethod(requiredRequest(request.getPaymentMethod(), 32));
        normalized.setSourceAppId(requiredRequest(request.getSourceAppId(), 64));
        normalized.setRequestedChannelCode(optionalRequest(request.getRequestedChannelCode(), 64));
        normalized.setResolvedChannelCode(requiredRequest(request.getResolvedChannelCode(), 64));
        normalized.setTerminal(requiredRequest(request.getTerminal(), 32));
        normalized.setClientIp(requiredRequest(request.getClientIp(), 64));
        normalized.setIdempotencyKey(requiredRequest(request.getIdempotencyKey(), 128));
        BigDecimal amount = request.getAmount();
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(MAX_AMOUNT) > 0) {
            throw invalidRequest();
        }
        normalized.setAmount(amount);
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

    private void normalizeAndValidate(
            PaymentChannelSubmitResultDTO result, PaymentChannelSubmitRequestDTO request) {
        result.setChannelTransactionNo(required(
                result.getChannelTransactionNo(), "channel transaction number", 128));
        result.setAttemptStatus(required(result.getAttemptStatus(), "attempt status", 32));
        result.setAttemptStatusType(required(result.getAttemptStatusType(), "attempt status type", 32)
                .toLowerCase(Locale.ROOT));
        result.setChannelReturnCode(required(result.getChannelReturnCode(), "channel return code", 64));
        if (!STATUS_TYPES.contains(result.getAttemptStatusType())) {
            throw submitFailed("gateway-access returned an invalid attempt status type");
        }
        result.setNativePaymentParams(normalizeNativePaymentParams(
                result.getNativePaymentParams(), request.getTerminal()));
    }

    private Map<String, String> normalizeNativePaymentParams(
            Map<String, String> params, String terminal) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        if (!"MINI_PROGRAM".equalsIgnoreCase(terminal)
                || !NATIVE_PAYMENT_PARAM_KEYS.equals(params.keySet())) {
            throw submitFailed("gateway-access returned invalid native payment parameters");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String key : NATIVE_PAYMENT_PARAM_KEYS) {
            normalized.put(key, required(params.get(key), key, 512));
        }
        return normalized;
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw submitFailed("gateway-access returned no " + fieldName);
        }
        if (containsIsoControl(value)) {
            throw submitFailed("gateway-access returned an invalid " + fieldName);
        }
        if (value.length() > maxLength) {
            throw submitFailed("gateway-access returned an invalid " + fieldName);
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

    private String buildResponsePayload(String channelCode, PaymentChannelSubmitResultDTO result) {
        return "{\"source\":\"gateway-access\",\"channelCode\":\""
                + escapeJson(channelCode) + "\",\"channelTransactionNo\":\""
                + escapeJson(result.getChannelTransactionNo()) + "\",\"channelReturnCode\":\""
                + escapeJson(result.getChannelReturnCode()) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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

    private BusinessException submitFailed(String message) {
        return BusinessException.sanitized(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, message);
    }

    private BusinessException invalidRequest() {
        return submitFailed("gateway-access payment submit request is invalid");
    }

    @Data
    public static class GatewaySubmitResponse {

        private String code;
        private String message;
        private PaymentChannelSubmitResultDTO data;
    }
}
