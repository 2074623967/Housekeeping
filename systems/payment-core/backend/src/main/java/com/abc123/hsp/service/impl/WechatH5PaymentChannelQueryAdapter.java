package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 微信 H5 支付渠道模拟查单适配器。
 */
@Component
@Order(10)
public class WechatH5PaymentChannelQueryAdapter implements PaymentChannelQueryAdapter {

    private static final String CHANNEL_CODE = "wx_h5";

    @Override
    public boolean supports(String channelCode) {
        return CHANNEL_CODE.equalsIgnoreCase(channelCode);
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus(paymentDetail.getStatus());
        result.setChannelTransactionNo(paymentDetail.getChannelTransactionNo());
        result.setQuerySource("WECHAT_SIMULATION");
        return result;
    }
}
