package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import com.abc123.hsp.service.PaymentRecordService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收款记录控制器。
 */
@RestController
@RequestMapping("/api/payment-records")
public class PaymentRecordController {

    private final PaymentRecordService paymentRecordService;

    public PaymentRecordController(PaymentRecordService paymentRecordService) {
        this.paymentRecordService = paymentRecordService;
    }

    /**
     * 查询统一、微信或银行卡维度的支付收款记录。
     */
    @GetMapping
    public ApiResponse<List<PaymentRecordRowDTO>> list(
            @RequestParam(defaultValue = "ALL") String recordType) {
        return ApiResponse.success(paymentRecordService.list(recordType));
    }
}
