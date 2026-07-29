package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 本地兜底渠道查单适配器。
 */
@Component
@Order(1000)
public class LocalPaymentChannelQueryAdapter implements PaymentChannelQueryAdapter {

    @Override
    public boolean supports(String channelCode) {
        return true;
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus(paymentDetail.getStatus());
        result.setChannelTransactionNo(paymentDetail.getChannelTransactionNo());
        result.setQuerySource("LOCAL_SIMULATION_FALLBACK");
        return result;
    }
}
