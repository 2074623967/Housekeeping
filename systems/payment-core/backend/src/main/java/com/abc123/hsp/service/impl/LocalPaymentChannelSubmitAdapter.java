package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 本地兜底渠道下单适配器。
 */
@Component
@Order(1000)
public class LocalPaymentChannelSubmitAdapter implements PaymentChannelSubmitAdapter {

    @Override
    public boolean supports(String channelCode) {
        return true;
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo("CHN-" + System.currentTimeMillis());
        result.setAttemptStatus("处理中");
        result.setAttemptStatusType("info");
        result.setResponsePayload(ChannelPayloadSupport.buildSuccessPayload(
                request.getResolvedChannelCode(),
                result.getChannelTransactionNo(),
                "local-fallback-gateway",
                "ACCEPTED"));
        return result;
    }
}
