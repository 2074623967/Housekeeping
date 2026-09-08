package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import com.abc123.hsp.service.PaymentConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付配置中心接口。
 */
@RestController
@RequestMapping("/api/payment-config")
public class PaymentConfigController {

    private final PaymentConfigService paymentConfigService;

    public PaymentConfigController(PaymentConfigService paymentConfigService) {
        this.paymentConfigService = paymentConfigService;
    }

    /**
     * 查询支付渠道、路由规则、支付协议和返回码映射配置。
     */
    @GetMapping
    public ApiResponse<PaymentConfigOverviewDTO> overview() {
        return ApiResponse.success(paymentConfigService.overview());
    }

    /**
     * 导出支付配置治理快照。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "ALL") String section) {
        byte[] csvBytes = paymentConfigService.exportGovernanceSnapshotCsv(section)
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-config-governance-snapshot.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }

    /**
     * 启停支付渠道。
     */
    @PostMapping("/channels/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleChannel(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleChannel(request));
    }

    /**
     * 提交支付路由规则启停候选进入复核。
     */
    @PostMapping("/route-rules/toggle")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> submitRouteRuleStatusReview(
            @RequestBody PaymentRouteRuleStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitRouteRuleStatusReview(request));
    }

    /**
     * 校验支付路由表达式并模拟样例命中。
     */
    @PostMapping("/route-rules/validate")
    public ApiResponse<PaymentRouteRuleValidationDTO> validateRouteRule(
            @RequestBody PaymentRouteRuleValidationRequestDTO request) {
        return ApiResponse.success(paymentConfigService.validateRouteRule(request));
    }

    /**
     * 检测当前启用支付路由规则之间的冲突。
     */
    @GetMapping("/route-rules/conflicts")
    public ApiResponse<List<PaymentRouteRuleConflictDTO>> listRouteRuleConflicts() {
        return ApiResponse.success(paymentConfigService.listRouteRuleConflicts());
    }

    /**
     * 查询支付路由规则复核申请。
     */
    @GetMapping("/route-rules/reviews")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> listRouteRuleReviews() {
        return ApiResponse.success(paymentConfigService.listRouteRuleReviews());
    }

    /**
     * 提交支付路由规则候选快照进入复核。
     */
    @PostMapping("/route-rules/reviews")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> submitRouteRuleReview(
            @RequestBody PaymentRouteRuleReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitRouteRuleReview(request));
    }

    /**
     * 通过复核并发布候选快照。
     */
    @PostMapping("/route-rules/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> approveRouteRuleReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentRouteRuleReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveRouteRuleReview(requestNo, request));
    }

    /**
     * 驳回复核申请。
     */
    @PostMapping("/route-rules/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> rejectRouteRuleReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentRouteRuleReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectRouteRuleReview(requestNo, request));
    }

    /**
     * 查询支付路由规则历史版本。
     */
    @GetMapping("/route-rules/{ruleCode}/versions")
    public ApiResponse<List<PaymentRouteRuleVersionDTO>> listRouteRuleVersions(
            @PathVariable("ruleCode") String ruleCode) {
        return ApiResponse.success(paymentConfigService.listRouteRuleVersions(ruleCode));
    }

    /**
     * 提交支付路由规则历史版本回滚候选进入复核。
     */
    @PostMapping("/route-rules/{ruleCode}/rollback")
    public ApiResponse<List<PaymentRouteRuleReviewDTO>> submitRouteRuleRollbackReview(
            @PathVariable("ruleCode") String ruleCode,
            @RequestBody PaymentRouteRuleRollbackRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitRouteRuleRollbackReview(ruleCode, request));
    }

    /**
     * 启停支付协议配置。
     */
    @PostMapping("/protocols/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleProtocol(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleProtocol(request));
    }

    /**
     * 新增支付协议配置。
     */
    @PostMapping("/protocols")
    public ApiResponse<PaymentConfigOverviewDTO> createProtocol(@RequestBody PaymentProtocolUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.createProtocol(request));
    }

    /**
     * 编辑支付协议配置。
     */
    @PutMapping("/protocols/{protocolCode}")
    public ApiResponse<PaymentConfigOverviewDTO> updateProtocol(@PathVariable("protocolCode") String protocolCode,
                                                                @RequestBody PaymentProtocolUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.updateProtocol(protocolCode, request));
    }

    /**
     * 新增异常告警值班路由配置。
     */
    @PostMapping("/issue-duty-rosters")
    public ApiResponse<PaymentConfigOverviewDTO> createIssueDutyRoster(
            @RequestBody PaymentIssueDutyRosterUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.createIssueDutyRoster(request));
    }

    /**
     * 编辑异常告警值班路由配置。
     */
    @PutMapping("/issue-duty-rosters/{rosterCode}")
    public ApiResponse<PaymentConfigOverviewDTO> updateIssueDutyRoster(
            @PathVariable("rosterCode") String rosterCode,
            @RequestBody PaymentIssueDutyRosterUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.updateIssueDutyRoster(rosterCode, request));
    }

    /**
     * 启停渠道返回码映射配置。
     */
    @PostMapping("/return-codes/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleReturnCodeMapping(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleReturnCodeMapping(request));
    }

    /**
     * 启停支付网关接入配置。
     */
    @PostMapping("/gateways/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleGateway(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleGateway(request));
    }

    /**
     * 启停支付控制策略配置。
     */
    @PostMapping("/control-policies/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleControlPolicy(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleControlPolicy(request));
    }

    /**
     * 启停告警通知供应商配置。
     */
    @PostMapping("/alert-providers/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleAlertProvider(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleAlertProvider(request));
    }

    /**
     * 执行支付控制策略自检。
     */
    @PostMapping("/control-policies/self-check")
    public ApiResponse<PaymentConfigOverviewDTO> runControlPolicySelfCheck(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.runControlPolicySelfCheck(request));
    }

    /**
     * 启停异常告警值班路由配置。
     */
    @PostMapping("/issue-duty-rosters/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleIssueDutyRoster(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleIssueDutyRoster(request));
    }
}
