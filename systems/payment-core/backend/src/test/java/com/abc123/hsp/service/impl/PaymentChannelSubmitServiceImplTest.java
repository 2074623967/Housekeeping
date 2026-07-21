package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 渠道下单适配器选择测试。
 */
class PaymentChannelSubmitServiceImplTest {

    @Test
    void shouldSelectWechatSubmitAdapterByChannelCode() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("wx_h5");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("处理中", result.getAttemptStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"wechat-gateway\""));
    }

    @Test
    void shouldSelectOfflineBankSubmitAdapterByChannelCode() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("offline_bank");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("待人工确认", result.getAttemptStatus());
        assertEquals("warn", result.getAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"offline-bank-gateway\""));
    }

    @Test
    void shouldFallbackToLocalSubmitAdapterForUnknownChannel() {
        PaymentChannelSubmitRequestDTO request = new PaymentChannelSubmitRequestDTO();
        request.setResolvedChannelCode("unknown_channel");

        PaymentChannelSubmitResultDTO result = buildService().submit(request);

        assertEquals("处理中", result.getAttemptStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getResponsePayload().contains("\"gatewayCode\":\"local-fallback-gateway\""));
    }

    private PaymentChannelSubmitServiceImpl buildService() {
        return new PaymentChannelSubmitServiceImpl(Arrays.asList(
                ordered(new WechatH5PaymentChannelSubmitAdapter()),
                ordered(new AlipayH5PaymentChannelSubmitAdapter()),
                ordered(new OfflineBankPaymentChannelSubmitAdapter()),
                ordered(new LocalPaymentChannelSubmitAdapter())
        ));
    }

    private PaymentChannelSubmitAdapter ordered(PaymentChannelSubmitAdapter adapter) {
        return adapter;
    }
}
