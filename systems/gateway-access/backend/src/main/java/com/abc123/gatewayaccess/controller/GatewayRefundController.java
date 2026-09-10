package com.abc123.gatewayaccess.controller;

import com.abc123.gatewayaccess.common.ApiResponse;
import com.abc123.gatewayaccess.dto.RefundOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitResultDTO;
import com.abc123.gatewayaccess.service.RefundOrderQueryService;
import com.abc123.gatewayaccess.service.RefundOrderSubmitService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向支付核心的退款代理接口。
 */
@RestController
@RequestMapping("/api/gateway/refunds")
public class GatewayRefundController {

    private final RefundOrderQueryService refundOrderQueryService;
    private final RefundOrderSubmitService refundOrderSubmitService;

    public GatewayRefundController(RefundOrderQueryService refundOrderQueryService,
                                   RefundOrderSubmitService refundOrderSubmitService) {
        this.refundOrderQueryService = refundOrderQueryService;
        this.refundOrderSubmitService = refundOrderSubmitService;
    }

    @PostMapping("/submit")
    public ApiResponse<RefundOrderSubmitResultDTO> submitRefundOrder(
            @RequestHeader(value = "X-Gateway-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-Gateway-Access-Key", required = false) String accessKey,
            @RequestBody RefundOrderSubmitRequestDTO request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refundOrderSubmitService.submit(
                appCode, accessKey, servletRequest.getRemoteAddr(), request));
    }

    @PostMapping("/query")
    public ApiResponse<RefundOrderQueryResultDTO> queryRefundOrder(
            @RequestHeader(value = "X-Gateway-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-Gateway-Access-Key", required = false) String accessKey,
            @RequestBody RefundOrderQueryRequestDTO request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(refundOrderQueryService.query(
                appCode, accessKey, servletRequest.getRemoteAddr(), request));
    }
}
