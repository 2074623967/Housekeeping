package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 线下银行转账渠道模拟查单适配器。
 */
@Component
@Order(30)
public class OfflineBankPaymentChannelQueryAdapter implements PaymentChannelQueryAdapter {

    private static final String CHANNEL_CODE = "offline_bank";

    @Override
    public boolean supports(String channelCode) {
        return CHANNEL_CODE.equalsIgnoreCase(channelCode);
    }

    @Override
    public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus(paymentDetail.getStatus());
        result.setChannelTransactionNo(paymentDetail.getChannelTransactionNo());
        result.setQuerySource("OFFLINE_BANK_SIMULATION");
        return result;
    }
}
