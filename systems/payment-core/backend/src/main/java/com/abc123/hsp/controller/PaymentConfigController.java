package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentConfigOverviewDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import org.springframework.web.bind.annotation.PathVariable;
import com.abc123.hsp.service.PaymentConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
     * 启停支付渠道。
     */
    @PostMapping("/channels/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleChannel(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleChannel(request));
    }

    /**
     * 启停支付路由规则。
     */
    @PostMapping("/route-rules/toggle")
    public ApiResponse<PaymentConfigOverviewDTO> toggleRouteRule(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.toggleRouteRule(request));
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
     * 执行支付控制策略自检。
     */
    @PostMapping("/control-policies/self-check")
    public ApiResponse<PaymentConfigOverviewDTO> runControlPolicySelfCheck(@RequestBody PaymentConfigToggleRequestDTO request) {
        return ApiResponse.success(paymentConfigService.runControlPolicySelfCheck(request));
    }
}
