package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.ClearingOrderDetailDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.service.ClearingOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 清分结果控制器。
 */
@RestController
@RequestMapping("/api/clearing/orders")
public class ClearingOrderController {

    private final ClearingOrderService clearingOrderService;

    public ClearingOrderController(ClearingOrderService clearingOrderService) {
        this.clearingOrderService = clearingOrderService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<ClearingOrderDTO>> list(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String clearingStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(clearingOrderService.list(batchNo, orderNo, clearingStatus, pageNo, pageSize));
    }

    @GetMapping("/{clearingNo}")
    public ApiResponse<ClearingOrderDetailDTO> detail(@PathVariable String clearingNo) {
        return ApiResponse.success(clearingOrderService.detail(clearingNo));
    }
}
