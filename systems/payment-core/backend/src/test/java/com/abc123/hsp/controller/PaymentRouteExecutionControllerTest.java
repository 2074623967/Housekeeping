package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付路由执行结果控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRouteExecutionControllerTest {

    @Mock
    private PaymentRouteExecutionService paymentRouteExecutionService;

    @Test
    void shouldListPaymentRouteExecutions() {
        PaymentRouteExecutionController controller = new PaymentRouteExecutionController(paymentRouteExecutionService);

        controller.list(null, null, null, null, "全部", "全部", "全部", "createdAt", "desc", 1, 20);

        verify(paymentRouteExecutionService).list(any(PaymentRouteExecutionQueryDTO.class));
    }

    @Test
    void shouldReturnPaymentRouteExecutionOverview() {
        PaymentRouteExecutionController controller = new PaymentRouteExecutionController(paymentRouteExecutionService);
        PaymentRouteExecutionOverviewDTO overviewDTO = new PaymentRouteExecutionOverviewDTO();
        overviewDTO.setTotalRouteCount(6L);
        when(paymentRouteExecutionService.overview(any(PaymentRouteExecutionQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("PAY-001", "ORD-001", "RULE_HOME_WX", "wx_h5", "微信", "H5", "命中规则路由", "createdAt", "desc");

        verify(paymentRouteExecutionService).overview(any(PaymentRouteExecutionQueryDTO.class));
    }
}
