package com.abc123.gatewayaccess.controller;

import com.abc123.gatewayaccess.common.ApiResponse;
import com.abc123.gatewayaccess.dto.GatewayAccessSummaryDTO;
import com.abc123.gatewayaccess.dto.GatewayAppDTO;
import com.abc123.gatewayaccess.dto.GatewayCertificateDTO;
import com.abc123.gatewayaccess.dto.GatewayChannelDTO;
import com.abc123.gatewayaccess.dto.GatewayPermissionDTO;
import com.abc123.gatewayaccess.dto.PageResultDTO;
import com.abc123.gatewayaccess.dto.ToggleRequestDTO;
import com.abc123.gatewayaccess.service.GatewayAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网关接入配置接口。
 */
@RestController
@RequestMapping("/api/gateway-access")
public class GatewayAccessController {

    private final GatewayAccessService service;

    public GatewayAccessController(GatewayAccessService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ApiResponse<GatewayAccessSummaryDTO> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/applications")
    public ApiResponse<PageResultDTO<GatewayAppDTO>> applications() {
        return ApiResponse.success(service.applications());
    }

    @GetMapping("/gateways")
    public ApiResponse<PageResultDTO<GatewayChannelDTO>> gateways() {
        return ApiResponse.success(service.gateways());
    }

    @GetMapping("/certificates")
    public ApiResponse<PageResultDTO<GatewayCertificateDTO>> certificates() {
        return ApiResponse.success(service.certificates());
    }

    @GetMapping("/permissions")
    public ApiResponse<PageResultDTO<GatewayPermissionDTO>> permissions() {
        return ApiResponse.success(service.permissions());
    }

    @PostMapping("/applications/toggle")
    public ApiResponse<GatewayAccessSummaryDTO> toggleApplication(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleApplication(request));
    }

    @PostMapping("/gateways/toggle")
    public ApiResponse<GatewayAccessSummaryDTO> toggleGateway(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleGateway(request));
    }

    @PostMapping("/certificates/toggle")
    public ApiResponse<GatewayAccessSummaryDTO> toggleCertificate(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.toggleCertificate(request));
    }

    @PostMapping("/permissions/toggle")
    public ApiResponse<GatewayAccessSummaryDTO> togglePermission(@RequestBody ToggleRequestDTO request) {
        return ApiResponse.success(service.togglePermission(request));
    }
}
