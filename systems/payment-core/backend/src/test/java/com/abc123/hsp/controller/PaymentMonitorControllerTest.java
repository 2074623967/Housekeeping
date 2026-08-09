package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.service.PaymentMonitorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付监控分析控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentMonitorControllerTest {

    @Mock
    private PaymentMonitorService paymentMonitorService;

    @Test
    void shouldReturnOverview() {
        PaymentMonitorOverviewDTO overview = new PaymentMonitorOverviewDTO();
        when(paymentMonitorService.overview()).thenReturn(overview);

        PaymentMonitorController controller = new PaymentMonitorController(paymentMonitorService);

        assertEquals(overview, controller.overview().getData());
        verify(paymentMonitorService).overview();
    }
}
