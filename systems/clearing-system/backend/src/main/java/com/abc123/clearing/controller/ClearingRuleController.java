package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.ClearingRuleDTO;
import com.abc123.clearing.dto.CreateClearingRuleRequestDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.service.ClearingRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 清分规则控制器。
 */
@RestController
@RequestMapping("/api/clearing/rules")
public class ClearingRuleController {

    private final ClearingRuleService clearingRuleService;

    public ClearingRuleController(ClearingRuleService clearingRuleService) {
        this.clearingRuleService = clearingRuleService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<ClearingRuleDTO>> list(
            @RequestParam(required = false) String ruleType,
            @RequestParam(required = false) String ruleStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(clearingRuleService.list(ruleType, ruleStatus, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<ClearingRuleDTO> create(@RequestBody CreateClearingRuleRequestDTO request) {
        return ApiResponse.success(clearingRuleService.create(request));
    }

    @PostMapping("/{ruleNo}/enable")
    public ApiResponse<ClearingRuleDTO> enable(@PathVariable String ruleNo) {
        return ApiResponse.success(clearingRuleService.enable(ruleNo));
    }

    @PostMapping("/{ruleNo}/disable")
    public ApiResponse<ClearingRuleDTO> disable(@PathVariable String ruleNo) {
        return ApiResponse.success(clearingRuleService.disable(ruleNo));
    }
}
