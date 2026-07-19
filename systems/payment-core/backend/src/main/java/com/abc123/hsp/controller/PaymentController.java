package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentCloseRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.service.PaymentService;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public ApiResponse<List<PaymentListItemDTO>> list() {
        return ApiResponse.success(paymentService.list());
    }

    @GetMapping("/{paymentOrderId}")
    public ApiResponse<PaymentDetailDTO> detail(@PathVariable String paymentOrderId) {
        return ApiResponse.success(paymentService.detail(paymentOrderId));
    }

    @PostMapping("/prepay")
    public ApiResponse<PrepayOrderDTO> prepay(@RequestBody PrepayRequestDTO request) {
        return ApiResponse.success(paymentService.prepay(request));
    }

    @GetMapping("/cashier/{prepayOrderNo}")
    public ApiResponse<CashierPageDTO> cashier(@PathVariable String prepayOrderNo) {
        return ApiResponse.success(paymentService.cashier(prepayOrderNo));
    }

    @PostMapping("/submit")
    public ApiResponse<PrepayOrderDTO> submit(@RequestBody PaymentSubmitRequestDTO request) {
        return ApiResponse.success(paymentService.submit(request));
    }

    @PostMapping("/callback/{channel}")
    public ApiResponse<PaymentDetailDTO> callback(@PathVariable String channel, @RequestBody PaymentCallbackRequestDTO request) {
        return ApiResponse.success(paymentService.callback(channel, request));
    }

    @PostMapping("/query")
    public ApiResponse<PaymentDetailDTO> query(@RequestBody PaymentQueryRequestDTO request) {
        return ApiResponse.success(paymentService.query(request));
    }

    @PostMapping("/close")
    public ApiResponse<PaymentDetailDTO> close(@RequestBody PaymentCloseRequestDTO request) {
        return ApiResponse.success(paymentService.close(request));
    }
}
