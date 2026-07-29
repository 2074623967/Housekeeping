package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.service.PaymentMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付监控分析控制器。
 */
@RestController
@RequestMapping("/api/payment-monitor")
public class PaymentMonitorController {

    private final PaymentMonitorService paymentMonitorService;

    public PaymentMonitorController(PaymentMonitorService paymentMonitorService) {
        this.paymentMonitorService = paymentMonitorService;
    }

    /**
     * 查询支付趋势、渠道表现和异常告警。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentMonitorOverviewDTO> overview() {
        return ApiResponse.success(paymentMonitorService.overview());
    }
}
