package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentMetricsDTO;
import com.abc123.hsp.service.PaymentMetricsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付健康指标控制器。
 */
@RestController
@RequestMapping("/api/payment-metrics")
public class PaymentMetricsController {

    private final PaymentMetricsService paymentMetricsService;

    public PaymentMetricsController(PaymentMetricsService paymentMetricsService) {
        this.paymentMetricsService = paymentMetricsService;
    }

    /**
     * 查询支付单成功率、金额和状态分布。
     */
    @GetMapping("/summary")
    public ApiResponse<PaymentMetricsDTO> summary() {
        return ApiResponse.success(paymentMetricsService.summary());
    }
}
