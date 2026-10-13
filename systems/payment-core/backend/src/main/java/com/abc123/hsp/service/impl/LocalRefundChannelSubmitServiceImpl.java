package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

/**
 * 本地退款渠道模拟实现。
 */
@Service
@Profile("demo")
public class LocalRefundChannelSubmitServiceImpl implements RefundChannelSubmitService {

    @Override
    public RefundChannelSubmitResultDTO submit(RefundChannelSubmitRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRefundAttemptNo())) {
            throw new IllegalArgumentException("退款尝试号不能为空");
        }
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-" + request.getRefundAttemptNo().trim());
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        result.setResponsePayload(buildResponsePayload(request, result.getChannelRefundNo()));
        return result;
    }

    private String buildResponsePayload(RefundChannelSubmitRequestDTO request, String channelRefundNo) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("code", "SUCCESS");
        payload.put("refundOrderId", safe(request.getRefundOrderId()));
        payload.put("refundAttemptNo", request.getRefundAttemptNo().trim());
        payload.put("paymentOrderId", safe(request.getPaymentOrderId()));
        payload.put("channelCode", safe(request.getChannelCode()));
        payload.put("channelRefundNo", channelRefundNo);
        try {
            return new ObjectMapper().writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模拟退款渠道响应序列化失败", exception);
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
