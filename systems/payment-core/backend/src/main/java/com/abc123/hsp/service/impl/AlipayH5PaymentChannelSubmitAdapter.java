package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 支付宝 H5 支付渠道模拟适配器。
 */
@Component
@Order(20)
public class AlipayH5PaymentChannelSubmitAdapter implements PaymentChannelSubmitAdapter {

    private static final String CHANNEL_CODE = "alipay_h5";

    @Override
    public boolean supports(String channelCode) {
        return CHANNEL_CODE.equalsIgnoreCase(channelCode);
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo("ALI-" + System.currentTimeMillis());
        result.setAttemptStatus("处理中");
        result.setAttemptStatusType("info");
        result.setResponsePayload(ChannelPayloadSupport.buildSuccessPayload(
                CHANNEL_CODE,
                result.getChannelTransactionNo(),
                "alipay-gateway",
                "ACCEPTED"));
        return result;
    }
}
