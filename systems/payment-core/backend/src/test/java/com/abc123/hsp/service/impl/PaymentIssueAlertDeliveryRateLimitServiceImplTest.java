package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.mapper.PaymentIssueAlertDeliveryRateLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 告警供应商派发共享限流服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueAlertDeliveryRateLimitServiceImplTest {

    @Mock
    private PaymentIssueAlertDeliveryRateLimitMapper mapper;

    @Test
    void shouldNormalizeDimensionsAndAcquireQuota() {
        when(mapper.acquire("ALERT_IM_WECOM", "IM", 60, 10)).thenReturn(1);

        boolean acquired = new PaymentIssueAlertDeliveryRateLimitServiceImpl(mapper)
                .tryAcquire(" alert_im_wecom ", " im ", 60, 10);

        assertTrue(acquired);
        verify(mapper).initBucket("ALERT_IM_WECOM", "IM");
        verify(mapper).acquire("ALERT_IM_WECOM", "IM", 60, 10);
    }

    @Test
    void shouldClampLimitsAndReportExhaustedQuota() {
        when(mapper.acquire("ALERT_SMS", "SMS", 1, 1)).thenReturn(0);

        boolean acquired = new PaymentIssueAlertDeliveryRateLimitServiceImpl(mapper)
                .tryAcquire("ALERT_SMS", "SMS", 0, 0);

        assertFalse(acquired);
        verify(mapper).initBucket("ALERT_SMS", "SMS");
        verify(mapper).acquire("ALERT_SMS", "SMS", 1, 1);
    }

    @Test
    void shouldFailClosedWhenBucketDimensionIsMissing() {
        assertFalse(new PaymentIssueAlertDeliveryRateLimitServiceImpl(mapper)
                .tryAcquire(" ", "IM", 60, 10));

        verifyNoInteractions(mapper);
    }
}
