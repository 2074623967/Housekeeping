package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.service.PaymentRequestService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付请求管理控制器。
 */
@RestController
@RequestMapping("/api/payment-requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    /**
     * 查询支付请求列表，供研发、运营和测试排查渠道请求过程。
     */
    @GetMapping
    public ApiResponse<List<PaymentRequestListItemDTO>> list(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String requestStatus) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(requestNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setRequestStatus(requestStatus);
        return ApiResponse.success(paymentRequestService.list(query));
    }
}
