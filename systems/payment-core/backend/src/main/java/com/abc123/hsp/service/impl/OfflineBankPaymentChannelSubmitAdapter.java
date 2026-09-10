package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 线下银行转账渠道模拟适配器。
 */
@Component
@Profile("demo")
@Order(30)
public class OfflineBankPaymentChannelSubmitAdapter implements PaymentChannelSubmitAdapter {

    private static final String CHANNEL_CODE = "offline_bank";

    @Override
    public boolean supports(String channelCode) {
        return CHANNEL_CODE.equalsIgnoreCase(channelCode);
    }

    @Override
    public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
        PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
        result.setChannelTransactionNo(ChannelPayloadSupport.buildSimulatedChannelTransactionNo("BANK-"));
        result.setChannelReturnCode("BANK_TRANSFER_PENDING");
        result.setAttemptStatus("待人工确认");
        result.setAttemptStatusType("warn");
        result.setResponsePayload(ChannelPayloadSupport.buildSuccessPayload(
                CHANNEL_CODE,
                result.getChannelTransactionNo(),
                "offline-bank-gateway",
                result.getChannelReturnCode()));
        return result;
    }
}
