package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<PageResultDTO<OrderListItemDTO>> list(
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String serviceType,
            @RequestParam(defaultValue = "全部") String orderStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        OrderQueryDTO query = new OrderQueryDTO();
        query.setOrderNo(orderNo);
        query.setServiceType(serviceType);
        query.setOrderStatus(orderStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(orderService.list(query));
    }
}
