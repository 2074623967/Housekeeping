package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.service.PaymentFlowService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付流水中心控制器。
 */
@RestController
@RequestMapping("/api/payment-flows")
public class PaymentFlowController {

    private final PaymentFlowService paymentFlowService;

    public PaymentFlowController(PaymentFlowService paymentFlowService) {
        this.paymentFlowService = paymentFlowService;
    }

    /**
     * 查询支付流水中心列表，供运营和测试排障使用。
     */
    @GetMapping
    public ApiResponse<List<PaymentFlowListItemDTO>> list() {
        return ApiResponse.success(paymentFlowService.list());
    }
}
