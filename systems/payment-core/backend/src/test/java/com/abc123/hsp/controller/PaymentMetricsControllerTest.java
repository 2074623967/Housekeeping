package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentMetricsDTO;
import com.abc123.hsp.service.PaymentMetricsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付健康指标控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentMetricsControllerTest {

    @Mock
    private PaymentMetricsService paymentMetricsService;

    @Test
    void shouldReturnPaymentMetricsSummary() {
        PaymentMetricsDTO metrics = new PaymentMetricsDTO();
        metrics.setTotalCount(12L);
        metrics.setSuccessCount(10L);
        metrics.setSuccessRate("83.33%");
        when(paymentMetricsService.summary()).thenReturn(metrics);

        PaymentMetricsController controller = new PaymentMetricsController(paymentMetricsService);

        assertEquals(metrics, controller.summary().getData());
        verify(paymentMetricsService).summary();
    }
}
