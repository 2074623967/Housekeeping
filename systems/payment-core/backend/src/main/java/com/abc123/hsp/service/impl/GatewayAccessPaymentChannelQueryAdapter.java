package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
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
 * gateway-access HTTP 主动查单适配器。
 */
@Component
@Order(1)
public class GatewayAccessPaymentChannelQueryAdapter implements PaymentChannelQueryAdapter {

    private final boolean enabled;
    private final String queryUrl;
    private final Set<String> supportedChannels;
    private final String appCode;
    private final String accessKey;
    private final RestTemplate restTemplate;

    @Autowired
    public GatewayAccessPaymentChannelQueryAdapter(
            @Value("${payment.channel-query.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.channel-query.gateway-access.query-url:}") String queryUrl,
            @Value("${payment.channel-query.gateway-access.supported-channels:wx_h5,alipay_h5,offline_bank}")
                    String supportedChannels,
            @Value("${payment.channel-query.gateway-access.app-code:APP_PAY_CORE}") String appCode,
            @Value("${payment.channel-query.gateway-access.access-key:}") String accessKey,
            @Value("${payment.channel-query.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, queryUrl, supportedChannels, appCode, accessKey,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    GatewayAccessPaymentChannelQueryAdapter(
            boolean enabled,
            String queryUrl,
            String supportedChannels,
            RestTemplate restTemplate) {
        this(enabled, queryUrl, supportedChannels, "APP_PAY_CORE", "", restTemplate);
    }

    GatewayAccessPaymentChannelQueryAdapter(
            boolean enabled,
            String queryUrl,
            String supportedChannels,
            String appCode,
            String accessKey,
            RestTemplate restTemplate) {
        this.enabled = enabled;
        this.queryUrl = queryUrl;
        this.supportedChannels = parseChannels(supportedChannels);
        this.appCode = appCode;
        this.accessKey = accessKey;
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(String channelCode) {
        return enabled
                && StringUtils.hasText(queryUrl)
                && StringUtils.hasText(appCode)
                && StringUtils.hasText(accessKey)
                && StringUtils.hasText(channelCode)
                && supportedChannels.contains(channelCode.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        try {
            GatewayQueryRequest request = buildRequest(paymentDetail);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Gateway-App-Code", appCode);
            headers.set("X-Gateway-Access-Key", accessKey);
            ResponseEntity<GatewayQueryResponse> responseEntity = restTemplate.exchange(
                    queryUrl.trim(), HttpMethod.POST, new HttpEntity<>(request, headers), GatewayQueryResponse.class);
            GatewayQueryResponse response = responseEntity.getBody();
            if (response == null || !"0".equals(response.getCode())) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                        "gateway-access rejected payment query");
            }
            PaymentChannelQueryResultDTO result = response.getData();
            if (result == null) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                        "gateway-access returned no definitive trade status");
            }
            if (result.getQuerySource() == null || result.getQuerySource().isEmpty()) {
                result.setQuerySource("GATEWAY_ACCESS_HTTP");
            }
            return PaymentChannelQueryResultValidator.validateAndNormalize(result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "gateway-access query failed, please retry later");
        }
    }

    private GatewayQueryRequest buildRequest(PaymentDetailDTO paymentDetail) {
        if (paymentDetail == null) {
            throw invalidRequest();
        }
        GatewayQueryRequest request = new GatewayQueryRequest();
        request.setPaymentOrderId(requiredRequest(paymentDetail.getPaymentOrderId(), 64));
        request.setOrderNo(requiredRequest(paymentDetail.getOrderNo(), 64));
        request.setChannelCode(requiredRequest(paymentDetail.getChannel(), 64));
        request.setChannelTransactionNo(optionalRequest(paymentDetail.getChannelTransactionNo(), 128));
        return request;
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
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidRequest();
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidRequest();
        }
        return normalized;
    }

    private BusinessException invalidRequest() {
        return new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                "gateway-access payment query request is invalid");
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

    @Data
    public static class GatewayQueryRequest {

        private String paymentOrderId;
        private String orderNo;
        private String channelCode;
        private String channelTransactionNo;
    }

    @Data
    public static class GatewayQueryResponse {

        private String code;
        private String message;
        private PaymentChannelQueryResultDTO data;
    }
}
