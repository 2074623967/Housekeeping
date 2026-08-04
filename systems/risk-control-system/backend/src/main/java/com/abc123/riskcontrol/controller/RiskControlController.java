package com.abc123.riskcontrol.controller;

import com.abc123.riskcontrol.common.ApiResponse;
import com.abc123.riskcontrol.dto.BlocklistDTO;
import com.abc123.riskcontrol.dto.InterceptEventDTO;
import com.abc123.riskcontrol.dto.LimitRuleDTO;
import com.abc123.riskcontrol.dto.MonitorRuleDTO;
import com.abc123.riskcontrol.dto.PageResultDTO;
import com.abc123.riskcontrol.dto.ReviewOrderDTO;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskPolicyDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.RiskSummaryDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import com.abc123.riskcontrol.service.RiskControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控后台接口。
 */
@RestController
@RequestMapping("/api/risk-control")
public class RiskControlController {

    private final RiskControlService service;

    public RiskControlController(RiskControlService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<RiskSummaryDTO> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/policies")
    public ApiResponse<PageResultDTO<RiskPolicyDTO>> policies() {
        return ApiResponse.success(service.policies());
    }

    @GetMapping("/limit-rules")
    public ApiResponse<PageResultDTO<LimitRuleDTO>> limitRules() {
        return ApiResponse.success(service.limitRules());
    }

    @GetMapping("/blocklists")
    public ApiResponse<PageResultDTO<BlocklistDTO>> blocklists() {
        return ApiResponse.success(service.blocklists());
    }

    @GetMapping("/intercept-events")
    public ApiResponse<PageResultDTO<InterceptEventDTO>> interceptEvents() {
        return ApiResponse.success(service.interceptEvents());
    }

    @GetMapping("/review-orders")
    public ApiResponse<PageResultDTO<ReviewOrderDTO>> reviewOrders() {
        return ApiResponse.success(service.reviewOrders());
    }

    @GetMapping("/monitor-rules")
    public ApiResponse<PageResultDTO<MonitorRuleDTO>> monitorRules() {
        return ApiResponse.success(service.monitorRules());
    }

    @PostMapping("/decisions/evaluate")
    public ApiResponse<RiskDecisionResultDTO> evaluateDecision(@RequestBody RiskDecisionRequestDTO request) {
        return ApiResponse.success(service.evaluatePaymentDecision(request));
    }

    @PostMapping("/policies/toggle")
    public ApiResponse<RiskSummaryDTO> togglePolicy(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.togglePolicy(request));
    }

    @PostMapping("/limit-rules/toggle")
    public ApiResponse<RiskSummaryDTO> toggleLimitRule(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleLimitRule(request));
    }

    @PostMapping("/blocklists/toggle")
    public ApiResponse<RiskSummaryDTO> toggleBlocklist(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleBlocklist(request));
    }

    @PostMapping("/monitor-rules/toggle")
    public ApiResponse<RiskSummaryDTO> toggleMonitorRule(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleMonitorRule(request));
    }

    @PostMapping("/review-orders/action")
    public ApiResponse<PageResultDTO<ReviewOrderDTO>> reviewAction(@RequestBody RiskReviewActionRequestDTO request) {
        return ApiResponse.success(service.reviewAction(request));
    }
}
