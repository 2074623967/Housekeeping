package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * 本地模拟渠道下单适配器。
 */
@Component
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
        result.setResponsePayload(new StringJoiner(",", "{", "}")
                .add("\"code\":\"SUCCESS\"")
                .add("\"channelCode\":\"" + request.getResolvedChannelCode() + "\"")
                .add("\"channelTransactionNo\":\"" + result.getChannelTransactionNo() + "\"")
                .add("\"message\":\"ACCEPTED\"")
                .toString());
        return result;
    }
}
