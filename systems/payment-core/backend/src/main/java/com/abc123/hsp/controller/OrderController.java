package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 查询订单中心列表，为支付发起和履约排查提供基础数据。
     */
    @GetMapping
    public ApiResponse<List<OrderListItemDTO>> list() {
        return ApiResponse.success(orderService.list());
    }
}
