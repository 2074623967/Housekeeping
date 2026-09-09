package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentChannelReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentChannelReviewDTO;
import com.abc123.hsp.dto.PaymentChannelReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewDTO;
import com.abc123.hsp.dto.PaymentControlPolicyReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyVersionDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewDTO;
import com.abc123.hsp.dto.PaymentGatewayReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterReviewDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportResultDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeImportReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentReturnCodeReviewDTO;
import com.abc123.hsp.dto.PaymentReturnCodeStatusReviewRequestDTO;
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

    /** 提交支付渠道启停候选进入复核。 */
    @PostMapping("/channels/toggle")
    public ApiResponse<List<PaymentChannelReviewDTO>> submitChannelStatusReview(
            @RequestBody PaymentChannelStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitChannelStatusReview(request));
    }

    /** 提交支付渠道新增或编辑完整候选进入复核。 */
    @PostMapping("/channels/reviews")
    public ApiResponse<List<PaymentChannelReviewDTO>> submitChannelReview(
            @RequestBody PaymentChannelReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitChannelReview(request));
    }

    /** 查询支付渠道状态变更复核申请。 */
    @GetMapping("/channels/reviews")
    public ApiResponse<List<PaymentChannelReviewDTO>> listChannelReviews() {
        return ApiResponse.success(paymentConfigService.listChannelReviews());
    }

    /** 通过支付渠道状态变更复核并应用候选。 */
    @PostMapping("/channels/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentChannelReviewDTO>> approveChannelReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentChannelReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveChannelReview(requestNo, request));
    }

    /** 驳回支付渠道状态变更复核，不改变运行态。 */
    @PostMapping("/channels/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentChannelReviewDTO>> rejectChannelReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentChannelReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectChannelReview(requestNo, request));
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
     * 提交支付协议启停候选进入复核。
     */
    @PostMapping("/protocols/toggle")
    public ApiResponse<List<PaymentProtocolReviewDTO>> submitProtocolStatusReview(
            @RequestBody PaymentProtocolStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitProtocolStatusReview(request));
    }

    /**
     * 提交支付协议候选快照进入复核。
     */
    @PostMapping("/protocols/reviews")
    public ApiResponse<List<PaymentProtocolReviewDTO>> submitProtocolReview(
            @RequestBody PaymentProtocolReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitProtocolReview(request));
    }

    /**
     * 查询支付协议复核申请。
     */
    @GetMapping("/protocols/reviews")
    public ApiResponse<List<PaymentProtocolReviewDTO>> listProtocolReviews() {
        return ApiResponse.success(paymentConfigService.listProtocolReviews());
    }

    /**
     * 通过复核并发布支付协议候选快照。
     */
    @PostMapping("/protocols/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentProtocolReviewDTO>> approveProtocolReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentProtocolReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveProtocolReview(requestNo, request));
    }

    /**
     * 驳回支付协议复核申请。
     */
    @PostMapping("/protocols/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentProtocolReviewDTO>> rejectProtocolReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentProtocolReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectProtocolReview(requestNo, request));
    }

    /**
     * 查询支付协议不可变版本历史。
     */
    @GetMapping("/protocols/{protocolCode}/versions")
    public ApiResponse<List<PaymentProtocolVersionDTO>> listProtocolVersions(
            @PathVariable("protocolCode") String protocolCode) {
        return ApiResponse.success(paymentConfigService.listProtocolVersions(protocolCode));
    }

    /**
     * 提交支付协议历史版本回滚候选进入复核。
     */
    @PostMapping("/protocols/{protocolCode}/rollback")
    public ApiResponse<List<PaymentProtocolReviewDTO>> submitProtocolRollbackReview(
            @PathVariable("protocolCode") String protocolCode,
            @RequestBody PaymentProtocolRollbackRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitProtocolRollbackReview(protocolCode, request));
    }

    /** 提交异常告警值班路由新增候选，不改变运行态。 */
    @PostMapping("/issue-duty-rosters")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> createIssueDutyRoster(
            @RequestBody PaymentIssueDutyRosterUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.createIssueDutyRoster(request));
    }

    /** 提交异常告警值班路由编辑候选，不改变运行态。 */
    @PutMapping("/issue-duty-rosters/{rosterCode}")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> updateIssueDutyRoster(
            @PathVariable("rosterCode") String rosterCode,
            @RequestBody PaymentIssueDutyRosterUpsertRequestDTO request) {
        return ApiResponse.success(paymentConfigService.updateIssueDutyRoster(rosterCode, request));
    }

    /** 查询异常告警值班路由配置变更复核申请。 */
    @GetMapping("/issue-duty-rosters/reviews")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> listIssueDutyRosterReviews() {
        return ApiResponse.success(paymentConfigService.listIssueDutyRosterReviews());
    }

    /** 通过复核并应用异常告警值班路由完整候选。 */
    @PostMapping("/issue-duty-rosters/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> approveIssueDutyRosterReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentIssueDutyRosterReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveIssueDutyRosterReview(requestNo, request));
    }

    /** 驳回异常告警值班路由完整候选，不改变运行态。 */
    @PostMapping("/issue-duty-rosters/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> rejectIssueDutyRosterReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentIssueDutyRosterReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectIssueDutyRosterReview(requestNo, request));
    }

    /** 提交渠道返回码启停候选进入复核。 */
    @PostMapping("/return-codes/toggle")
    public ApiResponse<List<PaymentReturnCodeReviewDTO>> submitReturnCodeStatusReview(
            @RequestBody PaymentReturnCodeStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitReturnCodeStatusReview(request));
    }

    /** 查询渠道返回码单条变更复核申请。 */
    @GetMapping("/return-codes/reviews")
    public ApiResponse<List<PaymentReturnCodeReviewDTO>> listReturnCodeReviews() {
        return ApiResponse.success(paymentConfigService.listReturnCodeReviews());
    }

    /** 通过渠道返回码单条变更复核并应用状态候选。 */
    @PostMapping("/return-codes/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentReturnCodeReviewDTO>> approveReturnCodeReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentReturnCodeReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveReturnCodeReview(requestNo, request));
    }

    /** 驳回渠道返回码单条变更复核，不改变运行态。 */
    @PostMapping("/return-codes/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentReturnCodeReviewDTO>> rejectReturnCodeReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentReturnCodeReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectReturnCodeReview(requestNo, request));
    }

    /**
     * 预检渠道返回码 CSV，不修改运行态配置。
     */
    @PostMapping("/return-codes/import/preview")
    public ApiResponse<PaymentReturnCodeImportResultDTO> previewReturnCodeImport(
            @RequestBody PaymentReturnCodeImportRequestDTO request) {
        return ApiResponse.success(paymentConfigService.previewReturnCodeImport(request));
    }

    /** 查询渠道返回码导入复核申请。 */
    @GetMapping("/return-codes/import/reviews")
    public ApiResponse<List<PaymentReturnCodeImportReviewDTO>> listReturnCodeImportReviews() {
        return ApiResponse.success(paymentConfigService.listReturnCodeImportReviews());
    }

    /** 提交渠道返回码导入候选进入复核。 */
    @PostMapping("/return-codes/import/reviews")
    public ApiResponse<List<PaymentReturnCodeImportReviewDTO>> submitReturnCodeImportReview(
            @RequestBody PaymentReturnCodeImportReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitReturnCodeImportReview(request));
    }

    /** 通过渠道返回码导入复核并应用候选。 */
    @PostMapping("/return-codes/import/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentReturnCodeImportReviewDTO>> approveReturnCodeImportReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentReturnCodeImportReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveReturnCodeImportReview(requestNo, request));
    }

    /** 驳回渠道返回码导入复核，不改变运行态。 */
    @PostMapping("/return-codes/import/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentReturnCodeImportReviewDTO>> rejectReturnCodeImportReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentReturnCodeImportReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectReturnCodeImportReview(requestNo, request));
    }

    /** 提交支付网关启停候选进入复核。 */
    @PostMapping("/gateways/toggle")
    public ApiResponse<List<PaymentGatewayReviewDTO>> submitGatewayStatusReview(
            @RequestBody PaymentGatewayStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitGatewayStatusReview(request));
    }

    /** 查询支付网关配置变更复核申请。 */
    @GetMapping("/gateways/reviews")
    public ApiResponse<List<PaymentGatewayReviewDTO>> listGatewayReviews() {
        return ApiResponse.success(paymentConfigService.listGatewayReviews());
    }

    /** 提交支付网关新增或编辑完整候选进入复核。 */
    @PostMapping("/gateways/reviews")
    public ApiResponse<List<PaymentGatewayReviewDTO>> submitGatewayReview(
            @RequestBody PaymentGatewayReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitGatewayReview(request));
    }

    /** 通过支付网关配置变更复核并应用候选。 */
    @PostMapping("/gateways/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentGatewayReviewDTO>> approveGatewayReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentGatewayReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveGatewayReview(requestNo, request));
    }

    /** 驳回支付网关配置变更复核，不改变运行态。 */
    @PostMapping("/gateways/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentGatewayReviewDTO>> rejectGatewayReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentGatewayReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectGatewayReview(requestNo, request));
    }

    /** 提交支付控制策略启停候选，不改变运行态。 */
    @PostMapping("/control-policies/toggle")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> submitControlPolicyStatusReview(
            @RequestBody PaymentControlPolicyStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitControlPolicyStatusReview(request));
    }

    /** 查询支付控制策略配置变更复核申请。 */
    @GetMapping("/control-policies/reviews")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> listControlPolicyReviews() {
        return ApiResponse.success(paymentConfigService.listControlPolicyReviews());
    }

    /** 提交支付控制策略新增或编辑完整候选，不改变运行态。 */
    @PostMapping("/control-policies/reviews")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> submitControlPolicyReview(
            @RequestBody PaymentControlPolicyReviewSubmitRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitControlPolicyReview(request));
    }

    /** 通过支付控制策略配置变更复核并应用候选。 */
    @PostMapping("/control-policies/reviews/{requestNo}/approve")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> approveControlPolicyReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentControlPolicyReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.approveControlPolicyReview(requestNo, request));
    }

    /** 驳回支付控制策略配置变更复核，不改变运行态。 */
    @PostMapping("/control-policies/reviews/{requestNo}/reject")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> rejectControlPolicyReview(
            @PathVariable("requestNo") String requestNo,
            @RequestBody PaymentControlPolicyReviewActionRequestDTO request) {
        return ApiResponse.success(paymentConfigService.rejectControlPolicyReview(requestNo, request));
    }

    /** 查询支付控制策略不可变版本历史。 */
    @GetMapping("/control-policies/{sourceAppId}/versions")
    public ApiResponse<List<PaymentControlPolicyVersionDTO>> listControlPolicyVersions(
            @PathVariable("sourceAppId") String sourceAppId) {
        return ApiResponse.success(paymentConfigService.listControlPolicyVersions(sourceAppId));
    }

    /** 提交支付控制策略历史版本回滚候选，不改变运行态。 */
    @PostMapping("/control-policies/{sourceAppId}/rollback")
    public ApiResponse<List<PaymentControlPolicyReviewDTO>> submitControlPolicyRollbackReview(
            @PathVariable("sourceAppId") String sourceAppId,
            @RequestBody PaymentControlPolicyRollbackRequestDTO request) {
        return ApiResponse.success(paymentConfigService.submitControlPolicyRollbackReview(sourceAppId, request));
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

    /** 提交异常告警值班路由启停候选，不改变运行态。 */
    @PostMapping("/issue-duty-rosters/toggle")
    public ApiResponse<List<PaymentIssueDutyRosterReviewDTO>> toggleIssueDutyRoster(
            @RequestBody PaymentIssueDutyRosterStatusReviewRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleIssueDutyRoster(request));
    }
}
