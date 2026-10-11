package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitService;
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
        String refundOrderId = safe(request.getRefundOrderId());
        String paymentOrderId = safe(request.getPaymentOrderId());
        String channelCode = safe(request.getChannelCode());
        return "{\"code\":\"SUCCESS\",\"refundOrderId\":\""
                + refundOrderId
                + "\",\"refundAttemptNo\":\"" + request.getRefundAttemptNo().trim()
                + "\",\"paymentOrderId\":\""
                + paymentOrderId
                + "\",\"channelCode\":\""
                + channelCode
                + "\",\"channelRefundNo\":\""
                + channelRefundNo
                + "\"}";
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
