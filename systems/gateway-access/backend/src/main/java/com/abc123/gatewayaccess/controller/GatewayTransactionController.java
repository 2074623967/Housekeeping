package com.abc123.gatewayaccess.controller;

import com.abc123.gatewayaccess.common.ApiResponse;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitResultDTO;
import com.abc123.gatewayaccess.service.PaymentOrderQueryService;
import com.abc123.gatewayaccess.service.PaymentOrderSubmitService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向支付核心的交易代理接口，与治理台接口隔离。
 */
@RestController
@RequestMapping("/api/gateway")
public class GatewayTransactionController {

    private final PaymentOrderQueryService paymentOrderQueryService;
    private final PaymentOrderSubmitService paymentOrderSubmitService;

    public GatewayTransactionController(PaymentOrderQueryService paymentOrderQueryService,
                                        PaymentOrderSubmitService paymentOrderSubmitService) {
        this.paymentOrderQueryService = paymentOrderQueryService;
        this.paymentOrderSubmitService = paymentOrderSubmitService;
    }

    @PostMapping("/payment-orders/submit")
    public ApiResponse<PaymentOrderSubmitResultDTO> submitPaymentOrder(
            @RequestHeader(value = "X-Gateway-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-Gateway-Access-Key", required = false) String accessKey,
            @RequestBody PaymentOrderSubmitRequestDTO request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(paymentOrderSubmitService.submit(
                appCode, accessKey, servletRequest.getRemoteAddr(), request));
    }

    @PostMapping("/payment-orders/query")
    public ApiResponse<PaymentOrderQueryResultDTO> queryPaymentOrder(
            @RequestHeader(value = "X-Gateway-App-Code", required = false) String appCode,
            @RequestHeader(value = "X-Gateway-Access-Key", required = false) String accessKey,
            @RequestBody PaymentOrderQueryRequestDTO request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(paymentOrderQueryService.query(
                appCode, accessKey, servletRequest.getRemoteAddr(), request));
    }
}
