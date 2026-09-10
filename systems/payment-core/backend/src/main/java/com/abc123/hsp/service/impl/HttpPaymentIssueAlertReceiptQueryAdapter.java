package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.service.PaymentIssueAlertReceiptQueryAdapter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 通过环境显式配置的通知集成网关查询告警供应商回执。
 */
@Component
@Order(1)
public class HttpPaymentIssueAlertReceiptQueryAdapter implements PaymentIssueAlertReceiptQueryAdapter {

    private static final Set<String> DELIVERY_STATUSES = new HashSet<String>(
            Arrays.asList("DELIVERED", "ACCEPTED", "FAILED"));

    private final boolean enabled;
    private final String queryUrl;
    private final Set<String> supportedChannels;
    private final String appCode;
    private final String accessKey;
    private final RestTemplate restTemplate;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public HttpPaymentIssueAlertReceiptQueryAdapter(
            @Value("${payment.issue-alert.receipt-query.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.issue-alert.receipt-query.gateway-access.query-url:}") String queryUrl,
            @Value("${payment.issue-alert.receipt-query.gateway-access.supported-channels:IM,SMS,EMAIL}")
                    String supportedChannels,
            @Value("${payment.issue-alert.receipt-query.gateway-access.app-code:APP_PAY_CORE}") String appCode,
            @Value("${payment.issue-alert.receipt-query.gateway-access.access-key:}") String accessKey,
            @Value("${payment.issue-alert.receipt-query.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, queryUrl, supportedChannels, appCode, accessKey,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs),
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    HttpPaymentIssueAlertReceiptQueryAdapter(boolean enabled,
                                             String queryUrl,
                                             String supportedChannels,
                                             String appCode,
                                             String accessKey,
                                             RestTemplate restTemplate) {
        this(enabled, queryUrl, supportedChannels, appCode, accessKey, restTemplate,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    HttpPaymentIssueAlertReceiptQueryAdapter(boolean enabled,
                                             String queryUrl,
                                             String supportedChannels,
                                             String appCode,
                                             String accessKey,
                                             RestTemplate restTemplate,
                                             PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.enabled = enabled;
        this.queryUrl = queryUrl;
        this.supportedChannels = parseChannels(supportedChannels);
        this.appCode = appCode;
        this.accessKey = accessKey;
        this.restTemplate = restTemplate;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    @Override
    public boolean supports(String channelCode) {
        return enabled
                && StringUtils.hasText(queryUrl)
                && StringUtils.hasText(appCode)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(channelCode)
                && supportedChannels.contains(channelCode.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public PaymentIssueAlertDeliveryResultDTO query(PaymentIssueAlertLogEntity acceptedDelivery) {
        ReceiptQueryRequest request = buildRequest(acceptedDelivery);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-App-Code", appCode.trim());
            headers.set("X-Gateway-Access-Key", accessKey.trim());
            ResponseEntity<ReceiptQueryResponse> responseEntity = restTemplate.exchange(
                    queryUrl.trim(), HttpMethod.POST, new HttpEntity<ReceiptQueryRequest>(request, headers),
                    ReceiptQueryResponse.class);
            ReceiptQueryResponse response = responseEntity.getBody();
            if (response == null || !"0".equals(response.getCode()) || response.getData() == null) {
                throw queryFailed();
            }
            return validateAndNormalize(response.getData());
        } catch (RuntimeException exception) {
            throw queryFailed();
        }
    }

    private ReceiptQueryRequest buildRequest(PaymentIssueAlertLogEntity acceptedDelivery) {
        if (acceptedDelivery == null) {
            throw queryFailed();
        }
        ReceiptQueryRequest request = new ReceiptQueryRequest();
        request.setAlertNo(required(acceptedDelivery.getAlertNo(), 64));
        request.setIssueNo(optional(acceptedDelivery.getIssueNo(), 128));
        request.setAlertChannel(required(acceptedDelivery.getAlertChannel(), 32)
                .toUpperCase(Locale.ROOT));
        request.setProviderCode(optional(acceptedDelivery.getProviderCode(), 64));
        request.setProviderReceiptNo(optional(acceptedDelivery.getProviderReceiptNo(), 128));
        return request;
    }

    private PaymentIssueAlertDeliveryResultDTO validateAndNormalize(PaymentIssueAlertDeliveryResultDTO result) {
        String status = required(result.getProviderDeliveryStatus(), 32).toUpperCase(Locale.ROOT);
        if (!DELIVERY_STATUSES.contains(status)) {
            throw queryFailed();
        }
        result.setProviderDeliveryStatus(status);
        result.setProviderReceiptNo(optional(result.getProviderReceiptNo(), 128));
        result.setProviderDeliveryMessage(optional(result.getProviderDeliveryMessage(), 255));
        if (StringUtils.hasText(result.getProviderReceiptSnapshot())) {
            String snapshot = result.getProviderReceiptSnapshot().trim();
            if (snapshot.length() > 2048) {
                throw queryFailed();
            }
            result.setProviderReceiptSnapshot(sensitiveDataMasker.maskProviderReceiptSnapshot(snapshot));
        }
        result.setRenderedContentSnapshot(null);
        return result;
    }

    private String required(String value, int maxLength) {
        String normalized = optional(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw queryFailed();
        }
        return normalized;
    }

    private String optional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (containsIsoControl(value)) {
            throw queryFailed();
        }
        String normalized = value.trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw queryFailed();
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

    private Set<String> parseChannels(String channels) {
        if (!StringUtils.hasText(channels)) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<String>();
        Arrays.stream(channels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private IllegalStateException queryFailed() {
        return new IllegalStateException("通知集成网关未返回可信的供应商送达回执");
    }

    @Data
    public static class ReceiptQueryRequest {

        private String alertNo;
        private String issueNo;
        private String alertChannel;
        private String providerCode;
        private String providerReceiptNo;
    }

    @Data
    public static class ReceiptQueryResponse {

        private String code;
        private String message;
        private PaymentIssueAlertDeliveryResultDTO data;
    }
}
