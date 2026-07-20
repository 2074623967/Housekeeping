package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.CreateFeeRuleRequestDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.service.FeeRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 费用规则控制器。
 */
@RestController
@RequestMapping("/api/clearing/fees")
public class FeeRuleController {

    private final FeeRuleService feeRuleService;

    public FeeRuleController(FeeRuleService feeRuleService) {
        this.feeRuleService = feeRuleService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<FeeRuleDTO>> list(
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(feeRuleService.list(feeType, status, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<FeeRuleDTO> create(@RequestBody CreateFeeRuleRequestDTO request) {
        return ApiResponse.success(feeRuleService.create(request));
    }
}
