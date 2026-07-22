package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.service.RefundChannelSubmitService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地退款渠道模拟实现。
 */
@Service
public class LocalRefundChannelSubmitServiceImpl implements RefundChannelSubmitService {

    private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    public RefundChannelSubmitResultDTO submit(RefundChannelSubmitRequestDTO request) {
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH" + LocalDateTime.now().format(NO_FORMATTER));
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        result.setResponsePayload(buildResponsePayload(request, result.getChannelRefundNo()));
        return result;
    }

    private String buildResponsePayload(RefundChannelSubmitRequestDTO request, String channelRefundNo) {
        String refundOrderId = request == null ? "" : safe(request.getRefundOrderId());
        String paymentOrderId = request == null ? "" : safe(request.getPaymentOrderId());
        String channelCode = request == null ? "" : safe(request.getChannelCode());
        return "{\"code\":\"SUCCESS\",\"refundOrderId\":\""
                + refundOrderId
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
