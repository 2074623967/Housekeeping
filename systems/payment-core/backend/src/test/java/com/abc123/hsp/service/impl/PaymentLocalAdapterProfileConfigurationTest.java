package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import com.abc123.hsp.service.RefundChannelSubmitService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 本地模拟支付适配器的环境隔离契约测试。
 */
class PaymentLocalAdapterProfileConfigurationTest {

    @Test
    void shouldNotRegisterLocalAdaptersWithoutDemoProfile() {
        try (AnnotationConfigApplicationContext context = createContext(null)) {
            assertEquals(0, context.getBeansOfType(PaymentChannelSubmitAdapter.class).size());
            assertEquals(0, context.getBeansOfType(PaymentChannelQueryAdapter.class).size());
            assertEquals(0, context.getBeansOfType(RefundChannelQueryAdapter.class).size());
            assertEquals(1, context.getBeansOfType(RefundChannelSubmitService.class).size());
            assertEquals(GatewayAccessRefundChannelSubmitServiceImpl.class,
                    context.getBean(RefundChannelSubmitService.class).getClass());
        }
    }

    @Test
    void shouldRegisterLocalAdaptersOnlyForDemoProfile() {
        try (AnnotationConfigApplicationContext context = createContext("demo")) {
            assertEquals(4, context.getBeansOfType(PaymentChannelSubmitAdapter.class).size());
            assertEquals(4, context.getBeansOfType(PaymentChannelQueryAdapter.class).size());
            assertEquals(1, context.getBeansOfType(RefundChannelQueryAdapter.class).size());
            assertEquals(1, context.getBeansOfType(RefundChannelSubmitService.class).size());
            assertEquals(LocalRefundChannelSubmitServiceImpl.class,
                    context.getBean(RefundChannelSubmitService.class).getClass());
        }
    }

    private AnnotationConfigApplicationContext createContext(String activeProfile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (activeProfile != null) {
            context.getEnvironment().setActiveProfiles(activeProfile);
        }
        context.register(
                WechatH5PaymentChannelSubmitAdapter.class,
                AlipayH5PaymentChannelSubmitAdapter.class,
                OfflineBankPaymentChannelSubmitAdapter.class,
                LocalPaymentChannelSubmitAdapter.class,
                WechatH5PaymentChannelQueryAdapter.class,
                AlipayH5PaymentChannelQueryAdapter.class,
                OfflineBankPaymentChannelQueryAdapter.class,
                LocalPaymentChannelQueryAdapter.class,
                LocalRefundChannelQueryAdapter.class,
                GatewayAccessRefundChannelSubmitServiceImpl.class,
                LocalRefundChannelSubmitServiceImpl.class);
        context.refresh();
        return context;
    }
}
