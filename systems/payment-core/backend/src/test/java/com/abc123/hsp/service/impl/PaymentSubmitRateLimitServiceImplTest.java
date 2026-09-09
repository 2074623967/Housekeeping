package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentSubmitRateLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付提交共享限流服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentSubmitRateLimitServiceImplTest {

    @Mock
    private PaymentSubmitRateLimitMapper mapper;

    @Test
    void shouldSkipScopesWithoutPositiveLimits() {
        new PaymentSubmitRateLimitServiceImpl(mapper)
                .acquire("app-a", "微信支付", "H5", "10.0.0.1", 0, -1);

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldHashDimensionsAndAcquireBothScopesInStableOrder() {
        when(mapper.acquire(eq("INTERFACE"), anyString(), eq(60), eq(2))).thenReturn(1);
        when(mapper.acquire(eq("SOURCE_METHOD"), anyString(), eq(60), eq(3))).thenReturn(1);

        new PaymentSubmitRateLimitServiceImpl(mapper)
                .acquire(" APP-A ", " 微信支付 ", " H5 ", " 10.0.0.1 ", 2, 3);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).initBucket(eq("INTERFACE"), keyCaptor.capture());
        verify(mapper).acquire(eq("INTERFACE"), keyCaptor.capture(), eq(60), eq(2));
        verify(mapper).initBucket(eq("SOURCE_METHOD"), keyCaptor.capture());
        verify(mapper).acquire(eq("SOURCE_METHOD"), keyCaptor.capture(), eq(60), eq(3));
        assertEquals(64, keyCaptor.getAllValues().get(0).length());
        assertEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(1));
        assertEquals(keyCaptor.getAllValues().get(2), keyCaptor.getAllValues().get(3));
        assertNotEquals(keyCaptor.getAllValues().get(0), keyCaptor.getAllValues().get(2));
    }

    @Test
    void shouldRejectBeforeMethodScopeWhenInterfaceQuotaIsExhausted() {
        when(mapper.acquire(eq("INTERFACE"), anyString(), eq(60), eq(2))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentSubmitRateLimitServiceImpl(mapper)
                        .acquire("app-a", "微信支付", "H5", "10.0.0.1", 2, 3));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED, exception.getCode());
        verify(mapper, never()).initBucket(eq("SOURCE_METHOD"), anyString());
    }

    @Test
    void shouldRejectWhenSourceMethodQuotaIsExhausted() {
        when(mapper.acquire(eq("INTERFACE"), anyString(), eq(60), eq(2))).thenReturn(1);
        when(mapper.acquire(eq("SOURCE_METHOD"), anyString(), eq(60), eq(3))).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentSubmitRateLimitServiceImpl(mapper)
                        .acquire("app-a", "微信支付", "H5", "10.0.0.1", 2, 3));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_RATE_LIMITED, exception.getCode());
    }
}
