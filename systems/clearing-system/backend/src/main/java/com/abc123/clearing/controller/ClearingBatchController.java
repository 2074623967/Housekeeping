package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.CreateClearingBatchRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.RerunClearingBatchRequestDTO;
import com.abc123.clearing.service.ClearingBatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 清分批次控制器。
 */
@RestController
@RequestMapping("/api/clearing/batches")
public class ClearingBatchController {

    private final ClearingBatchService clearingBatchService;

    public ClearingBatchController(ClearingBatchService clearingBatchService) {
        this.clearingBatchService = clearingBatchService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<ClearingBatchDTO>> list(
            @RequestParam(required = false) String batchDate,
            @RequestParam(required = false) String batchStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(clearingBatchService.list(batchDate, batchStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<ClearingBatchDTO> create(@RequestBody CreateClearingBatchRequestDTO request) {
        return ApiResponse.success(clearingBatchService.create(request));
    }

    @GetMapping("/{batchNo}")
    public ApiResponse<ClearingBatchDTO> detail(@PathVariable String batchNo) {
        return ApiResponse.success(clearingBatchService.detail(batchNo));
    }

    @PostMapping("/{batchNo}/rerun")
    public ApiResponse<ClearingBatchDTO> rerun(@PathVariable String batchNo, @RequestBody RerunClearingBatchRequestDTO request) {
        return ApiResponse.success(clearingBatchService.rerun(batchNo, request));
    }
}
