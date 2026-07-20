package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;

/**
 * 支付渠道下单适配器。
 */
public interface PaymentChannelSubmitAdapter {

    /**
     * 判断适配器是否支持指定渠道。
     */
    boolean supports(String channelCode);

    /**
     * 向渠道发起支付下单。
     */
    PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request);
}
