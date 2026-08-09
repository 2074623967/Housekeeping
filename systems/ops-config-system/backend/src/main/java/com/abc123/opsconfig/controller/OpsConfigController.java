package com.abc123.opsconfig.controller;

import com.abc123.opsconfig.common.ApiResponse;
import com.abc123.opsconfig.dto.AgreementTemplateDTO;
import com.abc123.opsconfig.dto.BusinessLineDTO;
import com.abc123.opsconfig.dto.CashierTemplateDTO;
import com.abc123.opsconfig.dto.ChannelProfileDTO;
import com.abc123.opsconfig.dto.OpsConfigEffectiveSnapshotDTO;
import com.abc123.opsconfig.dto.OpsConfigSnapshotQueryDTO;
import com.abc123.opsconfig.dto.OpsConfigSummaryDTO;
import com.abc123.opsconfig.dto.PageResultDTO;
import com.abc123.opsconfig.dto.PaymentTypeDTO;
import com.abc123.opsconfig.dto.RoutingRuleDTO;
import com.abc123.opsconfig.dto.SystemControlDTO;
import com.abc123.opsconfig.dto.ToggleRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeApprovalRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeCreateRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigChangeDTO;
import com.abc123.opsconfig.dto.OpsConfigChangePublishRequestDTO;
import com.abc123.opsconfig.dto.OpsConfigRefreshEventDTO;
import java.util.Map;
import java.util.HashMap;
import com.abc123.opsconfig.service.OpsConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运营配置后台接口。
 */
@RestController
@RequestMapping("/api/ops-config")
public class OpsConfigController {

    private final OpsConfigService service;

    public OpsConfigController(OpsConfigService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<OpsConfigSummaryDTO> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/agreement-templates")
    public ApiResponse<PageResultDTO<AgreementTemplateDTO>> agreementTemplates() {
        return ApiResponse.success(service.agreementTemplates());
    }

    @GetMapping("/business-lines")
    public ApiResponse<PageResultDTO<BusinessLineDTO>> businessLines() {
        return ApiResponse.success(service.businessLines());
    }

    @GetMapping("/payment-types")
    public ApiResponse<PageResultDTO<PaymentTypeDTO>> paymentTypes() {
        return ApiResponse.success(service.paymentTypes());
    }

    @GetMapping("/cashier-templates")
    public ApiResponse<PageResultDTO<CashierTemplateDTO>> cashierTemplates() {
        return ApiResponse.success(service.cashierTemplates());
    }

    @GetMapping("/channel-profiles")
    public ApiResponse<PageResultDTO<ChannelProfileDTO>> channelProfiles() {
        return ApiResponse.success(service.channelProfiles());
    }

    @GetMapping("/routing-rules")
    public ApiResponse<PageResultDTO<RoutingRuleDTO>> routingRules() {
        return ApiResponse.success(service.routingRules());
    }

    @GetMapping("/system-controls")
    public ApiResponse<PageResultDTO<SystemControlDTO>> systemControls() {
        return ApiResponse.success(service.systemControls());
    }

    @GetMapping("/effective-snapshot")
    public ApiResponse<OpsConfigEffectiveSnapshotDTO> effectiveSnapshot(@ModelAttribute OpsConfigSnapshotQueryDTO query) {
        return ApiResponse.success(service.effectiveSnapshot(query));
    }

    /**
     * 导出当前业务场景的有效配置快照。
     */
    @GetMapping("/effective-snapshot/export")
    public ResponseEntity<byte[]> exportEffectiveSnapshot(@ModelAttribute OpsConfigSnapshotQueryDTO query) {
        byte[] body = service.exportEffectiveSnapshot(query);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ops-config-effective-snapshot.csv")
                .body(body);
    }

    @PostMapping("/agreement-templates/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleAgreementTemplate(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleAgreementTemplate(request));
    }

    @PostMapping("/business-lines/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleBusinessLine(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleBusinessLine(request));
    }

    @PostMapping("/payment-types/toggle")
    public ApiResponse<OpsConfigSummaryDTO> togglePaymentType(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.togglePaymentType(request));
    }

    @PostMapping("/cashier-templates/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleCashierTemplate(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleCashierTemplate(request));
    }

    @PostMapping("/channel-profiles/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleChannelProfile(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleChannelProfile(request));
    }

    @PostMapping("/routing-rules/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleRoutingRule(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleRoutingRule(request));
    }

    @PostMapping("/system-controls/toggle")
    public ApiResponse<OpsConfigSummaryDTO> toggleSystemControl(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleSystemControl(request));
    }

    /**
     * 创建配置变更申请，进入待审批状态。
     */
    @PostMapping("/changes")
    public ApiResponse<OpsConfigChangeDTO> createConfigChange(
            @RequestBody OpsConfigChangeCreateRequestDTO request) {
        return ApiResponse.success(service.createConfigChange(request));
    }

    /**
     * 查询配置变更审批和发布记录。
     */
    @GetMapping("/changes")
    public ApiResponse<PageResultDTO<OpsConfigChangeDTO>> configChanges() {
        return ApiResponse.success(service.configChanges());
    }

    /**
     * 审批配置变更。
     */
    @PostMapping("/changes/approve")
    public ApiResponse<OpsConfigChangeDTO> approveConfigChange(
            @RequestBody OpsConfigChangeApprovalRequestDTO request) {
        return ApiResponse.success(service.approveConfigChange(request));
    }

    /**
     * 在灰度时间窗内发布配置变更。
     */
    @PostMapping("/changes/publish")
    public ApiResponse<OpsConfigChangeDTO> publishConfigChange(
            @RequestBody OpsConfigChangePublishRequestDTO request) {
        return ApiResponse.success(service.publishConfigChange(request));
    }

    /**
     * 查询配置快照刷新事件，供下游轮询或补偿消费。
     */
    @GetMapping("/refresh-events")
    public ApiResponse<PageResultDTO<OpsConfigRefreshEventDTO>> refreshEvents() {
        return ApiResponse.success(service.refreshEvents());
    }

    /**
     * 确认下游已消费指定配置刷新事件。
     */
    @PostMapping("/refresh-events/ack")
    public ApiResponse<Map<String, Object>> acknowledgeRefreshEvent(
            @RequestBody Map<String, String> request) {
        boolean acknowledged = service.acknowledgeRefreshEvent(
                request == null ? null : request.get("eventNo"),
                request == null ? null : request.get("acknowledgedBy"));
        Map<String, Object> result = new HashMap<>();
        result.put("acknowledged", acknowledged);
        return ApiResponse.success(result);
    }
}
