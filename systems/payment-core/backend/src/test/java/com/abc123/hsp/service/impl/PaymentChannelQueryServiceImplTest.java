package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 渠道查单适配器选择测试。
 */
class PaymentChannelQueryServiceImplTest {

    @Test
    void shouldSelectWechatQueryAdapterByChannelCode() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("wx_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("WX-001");

        PaymentDetailDTO result = buildService().query(detail);

        assertEquals("WECHAT_SIMULATION", result.getQuerySource());
        assertEquals("WX-001", result.getChannelTransactionNo());
    }

    @Test
    void shouldSelectAlipayQueryAdapterByChannelCode() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("alipay_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("ALI-001");

        PaymentDetailDTO result = buildService().query(detail);

        assertEquals("ALIPAY_SIMULATION", result.getQuerySource());
        assertEquals("ALI-001", result.getChannelTransactionNo());
    }

    @Test
    void shouldFallbackToLocalQueryAdapterForUnknownChannel() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setChannel("unknown_channel");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannelTransactionNo("UNK-001");

        PaymentDetailDTO result = buildService().query(detail);

        assertEquals("LOCAL_SIMULATION_FALLBACK", result.getQuerySource());
        assertEquals("UNK-001", result.getChannelTransactionNo());
    }

    private PaymentChannelQueryServiceImpl buildService() {
        return new PaymentChannelQueryServiceImpl(Arrays.asList(
                ordered(new WechatH5PaymentChannelQueryAdapter()),
                ordered(new AlipayH5PaymentChannelQueryAdapter()),
                ordered(new OfflineBankPaymentChannelQueryAdapter()),
                ordered(new LocalPaymentChannelQueryAdapter())
        ));
    }

    private PaymentChannelQueryAdapter ordered(PaymentChannelQueryAdapter adapter) {
        return adapter;
    }
}
