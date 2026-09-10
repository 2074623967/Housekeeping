package com.abc123.gatewayaccess.service.impl;

import com.abc123.gatewayaccess.common.GatewayAccessException;
import com.abc123.gatewayaccess.config.GatewayTransactionProperties;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.UpstreamPaymentQueryResponseDTO;
import com.abc123.gatewayaccess.mapper.GatewayAccessMapper;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 校验接入治理状态后，将查单请求转发给环境显式配置的真实渠道集成服务。
 */
@Service
public class PaymentOrderQueryServiceImpl implements PaymentOrderQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOrderQueryServiceImpl.class);
    private final GatewayTransactionAccessService accessService;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentOrderQueryServiceImpl(GatewayTransactionAccessService accessService,
                                        GatewayTransactionProperties properties,
                                        RestTemplateBuilder restTemplateBuilder) {
        this(accessService, restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()))
                .build());
    }

    PaymentOrderQueryServiceImpl(GatewayAccessMapper gatewayAccessMapper,
                                 GatewayTransactionProperties properties,
                                 RestTemplate restTemplate) {
        this(new GatewayTransactionAccessService(gatewayAccessMapper, properties), restTemplate);
    }

    PaymentOrderQueryServiceImpl(GatewayTransactionAccessService accessService, RestTemplate restTemplate) {
        this.accessService = accessService;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                            PaymentOrderQueryRequestDTO request) {
        String requestId = accessService.newRequestId();
        GatewayTransactionAccessService.AccessContext context = null;
        try {
            validateRequest(request);
            context = accessService.authorize(appCode, accessKey, request.getChannelCode(),
                    GatewayTransactionAccessService.PAYMENT_QUERY);
            request.setChannelCode(context.getChannelCode());
            UpstreamPaymentQueryResponseDTO response = restTemplate.postForObject(
                    context.getUpstreamUrl(), request, UpstreamPaymentQueryResponseDTO.class);
            PaymentOrderQueryResultDTO result = validateUpstreamResponse(response);
            if (!StringUtils.hasText(result.getQuerySource())) {
                result.setQuerySource("GATEWAY_ACCESS_PROXY");
            } else {
                result.setQuerySource(result.getQuerySource().trim());
            }
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "SUCCESS", "success", "渠道查单已由配置的真实上游返回确定状态");
            return result;
        } catch (GatewayAccessException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "FAILED", "danger", exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            accessService.audit(requestId, context, appCode, clientIp,
                    GatewayTransactionAccessService.PAYMENT_QUERY,
                    "FAILED", "danger", "GATEWAY_UPSTREAM_UNAVAILABLE");
            LOGGER.error("Payment query upstream failed, requestId={}, exceptionType={}",
                    requestId, exception.getClass().getName());
            throw new GatewayAccessException("GATEWAY_UPSTREAM_UNAVAILABLE",
                    "payment query upstream is unavailable, please retry later");
        }
    }

    private void validateRequest(PaymentOrderQueryRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || !StringUtils.hasText(request.getOrderNo()) || !StringUtils.hasText(request.getChannelCode())) {
            throw new GatewayAccessException("GATEWAY_INVALID_ARGUMENT",
                    "paymentOrderId, orderNo and channelCode are required");
        }
        request.setPaymentOrderId(request.getPaymentOrderId().trim());
        request.setOrderNo(request.getOrderNo().trim());
        request.setChannelCode(request.getChannelCode().trim().toLowerCase(Locale.ROOT));
        if (StringUtils.hasText(request.getChannelTransactionNo())) {
            request.setChannelTransactionNo(request.getChannelTransactionNo().trim());
        }
    }

    private PaymentOrderQueryResultDTO validateUpstreamResponse(UpstreamPaymentQueryResponseDTO response) {
        if (response == null || !"0".equals(response.getCode()) || response.getData() == null
                || !StringUtils.hasText(response.getData().getTradeStatus())) {
            throw new GatewayAccessException("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                    "payment query upstream returned no definitive trade status");
        }
        response.getData().setTradeStatus(response.getData().getTradeStatus().trim());
        if (StringUtils.hasText(response.getData().getChannelTransactionNo())) {
            response.getData().setChannelTransactionNo(response.getData().getChannelTransactionNo().trim());
        }
        return response.getData();
    }
}
