package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
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
 * gateway-access HTTP 退款主动查单适配器。
 */
@Component
@Order(1)
public class GatewayAccessRefundChannelQueryAdapter implements RefundChannelQueryAdapter {

    private final boolean enabled;
    private final String queryUrl;
    private final Set<String> supportedChannels;
    private final RestTemplate restTemplate;

    @Autowired
    public GatewayAccessRefundChannelQueryAdapter(
            @Value("${payment.refund-query.gateway-access.enabled:false}") boolean enabled,
            @Value("${payment.refund-query.gateway-access.query-url:}") String queryUrl,
            @Value("${payment.refund-query.gateway-access.supported-channels:wx_h5,alipay_h5,offline_bank}")
                    String supportedChannels,
            @Value("${payment.refund-query.gateway-access.timeout-ms:2000}") int timeoutMs) {
        this(enabled, queryUrl, supportedChannels,
                AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    GatewayAccessRefundChannelQueryAdapter(
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
    public RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund) {
        GatewayRefundQueryRequest request = new GatewayRefundQueryRequest();
        request.setRefundOrderId(refund.getRefundOrderId());
        request.setChannelCode(refund.getChannelCode());
        request.setChannelRefundNo(refund.getChannelRefundNo());
        try {
            GatewayRefundQueryResponse response = restTemplate.postForObject(
                    queryUrl.trim(), request, GatewayRefundQueryResponse.class);
            RefundChannelQueryResultDTO result = response == null ? null : response.getData();
            if (result == null) {
                throw queryFailed("gateway-access returned no refund query result");
            }
            if (!StringUtils.hasText(result.getQuerySource())) {
                result.setQuerySource("GATEWAY_ACCESS_HTTP");
            }
            return RefundChannelQueryResultValidator.validateAndNormalize(result);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw queryFailed("gateway-access refund query failed, please retry later");
        }
    }

    private Set<String> parseChannels(String channels) {
        if (!StringUtils.hasText(channels)) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<String>();
        Arrays.stream(channels.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }

    private BusinessException queryFailed(String message) {
        return new BusinessException(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, message);
    }

    @Data
    public static class GatewayRefundQueryRequest {

        private String refundOrderId;
        private String channelCode;
        private String channelRefundNo;
    }

    @Data
    public static class GatewayRefundQueryResponse {

        private String code;
        private String message;
        private RefundChannelQueryResultDTO data;
    }
}
