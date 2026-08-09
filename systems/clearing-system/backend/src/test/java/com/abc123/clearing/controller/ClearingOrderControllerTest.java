package com.abc123.clearing.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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

    @Test
    void shouldExportClearingOrders() {
        ClearingOrderController controller = new ClearingOrderController(clearingOrderService);
        when(clearingOrderService.exportList("CLB10001", "ORD202608030001", "PAY202608030001", "已完成"))
                .thenReturn("list".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportList("CLB10001", "ORD202608030001", "PAY202608030001", "已完成").getBody();

        verify(clearingOrderService).exportList("CLB10001", "ORD202608030001", "PAY202608030001", "已完成");
        assertEquals("list", new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void shouldExportClearingOrderDetail() {
        ClearingOrderController controller = new ClearingOrderController(clearingOrderService);
        when(clearingOrderService.exportDetail("CLO20001")).thenReturn("detail".getBytes(StandardCharsets.UTF_8));

        byte[] body = controller.exportDetail("CLO20001").getBody();

        verify(clearingOrderService).exportDetail("CLO20001");
        assertEquals("detail", new String(body, StandardCharsets.UTF_8));
    }
}
