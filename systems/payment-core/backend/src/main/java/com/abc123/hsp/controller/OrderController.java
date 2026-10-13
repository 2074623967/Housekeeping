package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderDetailDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.service.OrderService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
     * 查询标准订单及其最近账单、支付单和收银台关联事实。
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailDTO> detail(@PathVariable String orderNo) {
        return ApiResponse.success(orderService.detail(orderNo));
    }

    @GetMapping("/by-no")
    public ApiResponse<OrderDetailDTO> detailByNo(@RequestParam String orderNo) {
        return ApiResponse.success(orderService.detail(orderNo));
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

    /**
     * 导出订单中心列表，输出当前筛选条件下的 CSV 快照。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String serviceType,
            @RequestParam(defaultValue = "全部") String orderStatus) {
        OrderQueryDTO query = new OrderQueryDTO();
        query.setOrderNo(orderNo);
        query.setServiceType(serviceType);
        query.setOrderStatus(orderStatus);
        byte[] csvBytes = orderService.exportCsv(query).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=orders.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }
}
