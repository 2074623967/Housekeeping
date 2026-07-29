package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 微信 H5 支付渠道模拟适配器。
 */
@Component
@Order(10)
public class WechatH5PaymentChannelSubmitAdapter implements PaymentChannelSubmitAdapter {

    private static final String CHANNEL_CODE = "wx_h5";

    @Override
    public boolean supports(String channelCode) {
        return CHANNEL_CODE.equalsIgnoreCase(channelCode);
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo("WX-" + System.currentTimeMillis());
        result.setAttemptStatus("处理中");
        result.setAttemptStatusType("info");
        result.setResponsePayload(ChannelPayloadSupport.buildSuccessPayload(
                CHANNEL_CODE,
                result.getChannelTransactionNo(),
                "wechat-gateway",
                "USERPAYING"));
        return result;
    }
}
