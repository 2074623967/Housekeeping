package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentChannelQueryRateLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付渠道查单共享限流服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentChannelQueryRateLimitServiceImplTest {

    @Mock
    private PaymentChannelQueryRateLimitMapper mapper;

    @Test
    void shouldSkipRateLimitWhenDisabled() {
        new PaymentChannelQueryRateLimitServiceImpl(mapper, false, 60, 100)
                .acquire("wx_h5", "PAY-001");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldSkipRateLimitWhenGatewayQueryIsDisabled() {
        new PaymentChannelQueryRateLimitServiceImpl(mapper, true, false, 60, 100)
                .acquire("wx_h5", "PAY-001");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldNormalizeChannelAndAcquireSharedQuota() {
        when(mapper.acquire("wx_h5", 60, 100)).thenReturn(1);

        new PaymentChannelQueryRateLimitServiceImpl(mapper, true, 60, 100)
                .acquire(" WX_H5 ", "PAY-001");

        verify(mapper).initBucket("wx_h5");
        verify(mapper).acquire("wx_h5", 60, 100);
    }

    @Test
    void shouldRejectQueryWhenSharedQuotaIsExhausted() {
        when(mapper.acquire("alipay_h5", 1, 1)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentChannelQueryRateLimitServiceImpl(mapper, true, 0, 0)
                        .acquire("alipay_h5", "PAY-LIMITED"));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_RATE_LIMITED, exception.getCode());
        verify(mapper).initBucket("alipay_h5");
        verify(mapper).acquire("alipay_h5", 1, 1);
        verify(mapper).insertRejection(
                anyString(),
                org.mockito.ArgumentMatchers.eq("alipay_h5"),
                org.mockito.ArgumentMatchers.eq("PAY-LIMITED"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1));
        verify(mapper, never()).acquire("alipay_h5", 0, 0);
    }
}
