package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;

/**
 * 支付渠道下单服务。
 */
public interface PaymentChannelSubmitService {

    /**
     * 根据渠道编码选择适配器并发起下单。
     */
    PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request);
}
