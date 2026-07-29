package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.ApproveAdjustmentRequestDTO;
import com.abc123.accounting.dto.CreateAdjustmentRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.AdjustmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调账控制器。
 */
@RestController
@RequestMapping("/api/accounting/adjustments")
public class AdjustmentController {

    private final AdjustmentService adjustmentService;

    public AdjustmentController(AdjustmentService adjustmentService) {
        this.adjustmentService = adjustmentService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<AdjustmentItemDTO>> list(
            @RequestParam(required = false) String accountNo,
            @RequestParam(required = false) String adjustStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(adjustmentService.list(accountNo, adjustStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<AdjustmentItemDTO> create(@RequestBody CreateAdjustmentRequestDTO request) {
        return ApiResponse.success(adjustmentService.create(request));
    }

    @PostMapping("/{adjustNo}/approve")
    public ApiResponse<AdjustmentItemDTO> approve(@PathVariable String adjustNo,
            @RequestBody ApproveAdjustmentRequestDTO request) {
        return ApiResponse.success(adjustmentService.approve(adjustNo, request));
    }
}
