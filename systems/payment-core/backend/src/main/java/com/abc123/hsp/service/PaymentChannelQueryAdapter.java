package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;

/**
 * 支付渠道查单适配器。
 */
public interface PaymentChannelQueryAdapter {

    /**
     * 判断适配器是否支持指定渠道。
     */
    boolean supports(String channelCode);

    /**
     * 向渠道查询支付结果。
     */
    PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail);
}
