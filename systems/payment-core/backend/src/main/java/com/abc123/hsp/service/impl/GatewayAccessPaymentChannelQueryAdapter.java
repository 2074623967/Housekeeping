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
    private final RestTemplate restTemplate;

    @Autowired
    public GatewayAccessPaymentChannelQueryAdapter(
            @Value("${payment.channel-query.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.channel-query.gateway-access.query-url:}") String queryUrl,
            @Value("${payment.channel-query.gateway-access.supported-channels:wx_h5,alipay_h5,offline_bank}")
                    String supportedChannels,
            @Value("${payment.channel-query.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, queryUrl, supportedChannels,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    GatewayAccessPaymentChannelQueryAdapter(
            boolean enabled,
            String queryUrl,
            String supportedChannels,
            RestTemplate restTemplate) {
        this.enabled = enabled;
        this.queryUrl = queryUrl;
        this.supportedChannels = parseChannels(supportedChannels);
        this.restTemplate = restTemplate;
    }

    @Override
    public boolean supports(String channelCode) {
        return enabled
                && StringUtils.hasText(queryUrl)
                && StringUtils.hasText(channelCode)
                && supportedChannels.contains(channelCode.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        GatewayQueryRequest request = new GatewayQueryRequest();
        request.setPaymentOrderId(paymentDetail.getPaymentOrderId());
        request.setOrderNo(paymentDetail.getOrderNo());
        request.setChannelCode(paymentDetail.getChannel());
        request.setChannelTransactionNo(paymentDetail.getChannelTransactionNo());
        try {
            GatewayQueryResponse response = restTemplate.postForObject(
                    queryUrl.trim(), request, GatewayQueryResponse.class);
            PaymentChannelQueryResultDTO result = response == null ? null : response.getData();
            if (result == null || !StringUtils.hasText(result.getTradeStatus())) {
                throw new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                        "gateway-access returned no definitive trade status");
            }
            result.setTradeStatus(result.getTradeStatus().trim());
            if (StringUtils.hasText(result.getChannelTransactionNo())) {
                result.setChannelTransactionNo(result.getChannelTransactionNo().trim());
            }
            if (!StringUtils.hasText(result.getQuerySource())) {
                result.setQuerySource("GATEWAY_ACCESS_HTTP");
            } else {
                result.setQuerySource(result.getQuerySource().trim());
            }
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                    "gateway-access query failed, please retry later");
        }
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
