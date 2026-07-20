package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreateSettlementOrderRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.dto.SettlementOrderDetailDTO;
import com.abc123.settlement.service.SettlementOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算单控制器。
 */
@RestController
@RequestMapping("/api/settlements/orders")
public class SettlementOrderController {

    private final SettlementOrderService settlementOrderService;

    public SettlementOrderController(SettlementOrderService settlementOrderService) {
        this.settlementOrderService = settlementOrderService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<SettlementOrderDTO>> list(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(settlementOrderService.list(batchNo, targetType, settlementStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<SettlementOrderDTO> create(@RequestBody CreateSettlementOrderRequestDTO request) {
        return ApiResponse.success(settlementOrderService.create(request));
    }

    @GetMapping("/{settlementNo}")
    public ApiResponse<SettlementOrderDTO> detail(@PathVariable String settlementNo) {
        return ApiResponse.success(settlementOrderService.detail(settlementNo));
    }

    @GetMapping("/{settlementNo}/full")
    public ApiResponse<SettlementOrderDetailDTO> fullDetail(@PathVariable String settlementNo) {
        return ApiResponse.success(settlementOrderService.fullDetail(settlementNo));
    }

    @PostMapping("/{settlementNo}/audit")
    public ApiResponse<SettlementOrderDTO> audit(@PathVariable String settlementNo, @RequestBody AuditSettlementRequestDTO request) {
        return ApiResponse.success(settlementOrderService.audit(settlementNo, request));
    }

    @PostMapping("/{settlementNo}/reject")
    public ApiResponse<SettlementOrderDTO> reject(@PathVariable String settlementNo, @RequestBody AuditSettlementRequestDTO request) {
        return ApiResponse.success(settlementOrderService.reject(settlementNo, request));
    }
}
