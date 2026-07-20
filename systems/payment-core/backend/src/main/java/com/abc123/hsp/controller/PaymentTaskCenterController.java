package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.service.PaymentTaskCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付任务中心控制器。
 */
@RestController
@RequestMapping("/api/payment-task-center")
public class PaymentTaskCenterController {

    private final PaymentTaskCenterService paymentTaskCenterService;

    public PaymentTaskCenterController(PaymentTaskCenterService paymentTaskCenterService) {
        this.paymentTaskCenterService = paymentTaskCenterService;
    }

    /**
     * 查询支付任务中心总览。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentTaskCenterOverviewDTO> overview() {
        return ApiResponse.success(paymentTaskCenterService.overview());
    }

    /**
     * 手动执行超时关单任务。
     */
    @PostMapping("/close-expired-payments")
    public ApiResponse<PaymentTaskActionResultDTO> runCloseExpiredPayments() {
        return ApiResponse.success(paymentTaskCenterService.runCloseExpiredPayments());
    }

    /**
     * 手动执行失败事件重发。
     */
    @PostMapping("/republish-failed-events")
    public ApiResponse<PaymentTaskActionResultDTO> runRepublishFailedEvents() {
        return ApiResponse.success(paymentTaskCenterService.runRepublishFailedEvents());
    }

    /**
     * 手动执行失败退款重试。
     */
    @PostMapping("/retry-failed-refunds")
    public ApiResponse<PaymentTaskActionResultDTO> runRetryFailedRefunds() {
        return ApiResponse.success(paymentTaskCenterService.runRetryFailedRefunds());
    }
}
