package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.mapper.PaymentIssueAlertReceiptQueryRateLimitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 告警供应商回执查询共享限流服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueAlertReceiptQueryRateLimitServiceImplTest {

    @Mock
    private PaymentIssueAlertReceiptQueryRateLimitMapper mapper;

    @Test
    void shouldSkipRateLimitWhenDisabled() {
        new PaymentIssueAlertReceiptQueryRateLimitServiceImpl(mapper, false, 60, 100)
                .acquire("IM", "PIA-IM-001");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldSkipRateLimitWhenGatewayQueryIsDisabled() {
        new PaymentIssueAlertReceiptQueryRateLimitServiceImpl(mapper, true, false, 60, 100)
                .acquire("IM", "PIA-IM-001");

        verifyNoInteractions(mapper);
    }

    @Test
    void shouldNormalizeChannelAndAcquireSharedQuota() {
        when(mapper.acquire("IM", 60, 100)).thenReturn(1);

        new PaymentIssueAlertReceiptQueryRateLimitServiceImpl(mapper, true, 60, 100)
                .acquire(" im ", "PIA-IM-001");

        verify(mapper).initBucket("IM");
        verify(mapper).acquire("IM", 60, 100);
    }

    @Test
    void shouldRecordAndRejectWhenSharedQuotaIsExhausted() {
        when(mapper.acquire("SMS", 1, 1)).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentIssueAlertReceiptQueryRateLimitServiceImpl(mapper, true, 0, 0)
                        .acquire("SMS", "PIA-SMS-LIMITED"));

        assertEquals(ErrorCode.PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMITED, exception.getCode());
        verify(mapper).initBucket("SMS");
        verify(mapper).acquire("SMS", 1, 1);
        verify(mapper).insertRejection(
                anyString(),
                org.mockito.ArgumentMatchers.eq("SMS"),
                org.mockito.ArgumentMatchers.eq("PIA-SMS-LIMITED"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1));
        verify(mapper, never()).acquire("SMS", 0, 0);
    }
}
