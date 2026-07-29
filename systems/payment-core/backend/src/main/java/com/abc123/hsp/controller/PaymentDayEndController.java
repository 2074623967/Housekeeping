package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.service.PaymentDayEndService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付日终处理控制器。
 */
@RestController
@RequestMapping("/api/payment-day-end")
public class PaymentDayEndController {

    private final PaymentDayEndService paymentDayEndService;

    public PaymentDayEndController(PaymentDayEndService paymentDayEndService) {
        this.paymentDayEndService = paymentDayEndService;
    }

    /**
     * 查询支付日终处理总览。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentDayEndOverviewDTO> overview() {
        return ApiResponse.success(paymentDayEndService.overview());
    }

    /**
     * 手动触发支付日终处理。
     */
    @PostMapping("/run")
    public ApiResponse<PaymentDayEndOverviewDTO> run(@RequestBody(required = false) PaymentDayEndRunRequestDTO request) {
        return ApiResponse.success(paymentDayEndService.run(request));
    }
}
