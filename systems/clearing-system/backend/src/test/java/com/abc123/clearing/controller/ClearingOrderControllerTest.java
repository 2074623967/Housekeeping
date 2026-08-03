package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.service.ClearingOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 清分结果控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingOrderControllerTest {

    @Mock
    private ClearingOrderService clearingOrderService;

    @Test
    void shouldListClearingOrders() {
        ClearingOrderController controller = new ClearingOrderController(clearingOrderService);

        controller.list("CLB10001", "ORD202608030001", "PAY202608030001", "已完成", 1, 20);

        verify(clearingOrderService).list(
                "CLB10001",
                "ORD202608030001",
                "PAY202608030001",
                "已完成",
                1,
                20);
    }

    @Test
    void shouldReturnClearingOrderDetail() {
        ClearingOrderController controller = new ClearingOrderController(clearingOrderService);

        controller.detail("CLO20001");

        verify(clearingOrderService).detail("CLO20001");
    }
}
