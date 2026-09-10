package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    private static final Set<String> STATUS_TYPES = new HashSet<>(
            Arrays.asList("info", "warn", "danger", "success"));

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
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-App-Code", appCode);
            headers.set("X-Gateway-Access-Key", accessKey);
            ResponseEntity<GatewaySubmitResponse> responseEntity = restTemplate.exchange(
                    submitUrl.trim(), HttpMethod.POST, new HttpEntity<>(request, headers),
                    GatewaySubmitResponse.class);
            GatewaySubmitResponse response = responseEntity.getBody();
            if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
                throw submitFailed("gateway-access rejected payment submit");
            }
            PaymentChannelSubmitResultDTO result = response.getData();
            normalizeAndValidate(result);
            result.setResponsePayload(buildResponsePayload(request.getResolvedChannelCode(), result));
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw submitFailed("gateway-access payment submit failed; query before retrying");
        }
    }

    private void normalizeAndValidate(PaymentChannelSubmitResultDTO result) {
        result.setChannelTransactionNo(required(
                result.getChannelTransactionNo(), "channel transaction number", 128));
        result.setAttemptStatus(required(result.getAttemptStatus(), "attempt status", 32));
        result.setAttemptStatusType(required(result.getAttemptStatusType(), "attempt status type", 32)
                .toLowerCase(Locale.ROOT));
        result.setChannelReturnCode(required(result.getChannelReturnCode(), "channel return code", 64));
        if (!STATUS_TYPES.contains(result.getAttemptStatusType())) {
            throw submitFailed("gateway-access returned an invalid attempt status type");
        }
    }

    private String required(String value, String fieldName, int maxLength) {
        if (!StringUtils.hasText(value)) {
            throw submitFailed("gateway-access returned no " + fieldName);
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength || containsIsoControl(normalized)) {
            throw submitFailed("gateway-access returned an invalid " + fieldName);
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
        return new BusinessException(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, message);
    }

    @Data
    public static class GatewaySubmitResponse {

        private String code;
        private String message;
        private PaymentChannelSubmitResultDTO data;
    }
}
