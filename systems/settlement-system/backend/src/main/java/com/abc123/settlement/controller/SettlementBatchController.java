package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.dto.CreateSettlementBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementBatchDTO;
import com.abc123.settlement.service.SettlementBatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 结算批次控制器。
 */
@RestController
@RequestMapping("/api/settlements/batches")
public class SettlementBatchController {

    private final SettlementBatchService settlementBatchService;

    public SettlementBatchController(SettlementBatchService settlementBatchService) {
        this.settlementBatchService = settlementBatchService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<SettlementBatchDTO>> list(
            @RequestParam(required = false) String batchDate,
            @RequestParam(required = false) String batchStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(settlementBatchService.list(batchDate, batchStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<SettlementBatchDTO> create(@RequestBody CreateSettlementBatchRequestDTO request) {
        return ApiResponse.success(settlementBatchService.create(request));
    }

    @GetMapping("/{batchNo}")
    public ApiResponse<SettlementBatchDTO> detail(@PathVariable String batchNo) {
        return ApiResponse.success(settlementBatchService.detail(batchNo));
    }
}
