package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentMetricsDTO;
import com.abc123.hsp.mapper.PaymentMetricsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付健康指标服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentMetricsServiceImplTest {

    @Mock
    private PaymentMetricsMapper paymentMetricsMapper;

    @Test
    void shouldReturnEmptyMetricsWhenMapperReturnsNull() {
        when(paymentMetricsMapper.findSummary()).thenReturn(null);

        PaymentMetricsDTO summary = new PaymentMetricsServiceImpl(paymentMetricsMapper).summary();

        assertEquals(0L, summary.getTotalCount());
        assertEquals(0L, summary.getSuccessCount());
        assertEquals(0L, summary.getPendingCount());
        assertEquals(0L, summary.getClosedCount());
        assertEquals("¥0.00", summary.getSuccessAmount());
        assertEquals("0.00%", summary.getSuccessRate());
        verify(paymentMetricsMapper).findSummary();
    }

    @Test
    void shouldReturnMapperSummaryWhenPresent() {
        PaymentMetricsDTO expected = new PaymentMetricsDTO();
        expected.setTotalCount(3L);
        expected.setSuccessRate("66.67%");
        when(paymentMetricsMapper.findSummary()).thenReturn(expected);

        PaymentMetricsDTO actual = new PaymentMetricsServiceImpl(paymentMetricsMapper).summary();

        assertEquals(expected, actual);
    }
}
