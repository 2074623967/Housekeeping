package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.service.PaymentLogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付处理日志控制器。
 */
@RestController
@RequestMapping("/api/payment-logs")
public class PaymentLogController {

    private final PaymentLogService paymentLogService;

    public PaymentLogController(PaymentLogService paymentLogService) {
        this.paymentLogService = paymentLogService;
    }

    /**
     * 查询支付处理日志，供运营、研发和测试定位支付链路问题。
     */
    @GetMapping
    public ApiResponse<List<PaymentLogListItemDTO>> list() {
        return ApiResponse.success(paymentLogService.list());
    }
}
