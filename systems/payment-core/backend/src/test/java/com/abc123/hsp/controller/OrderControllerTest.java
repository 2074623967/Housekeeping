package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单中心控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @Test
    void shouldListOrders() {
        OrderController controller = new OrderController(orderService);

        controller.list("ORD-001", "深度保洁", "待支付", 1, 20);

        verify(orderService).list(any(OrderQueryDTO.class));
    }
}
