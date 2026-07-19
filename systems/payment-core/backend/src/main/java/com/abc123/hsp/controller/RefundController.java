package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.service.RefundService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ApiResponse<List<RefundListItemDTO>> list(
            @RequestParam(required = false) String refundOrderId,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String refundStatus,
            @RequestParam(defaultValue = "全部") String refundMethod) {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId(refundOrderId);
        query.setPaymentOrderId(paymentOrderId);
        query.setRefundStatus(refundStatus);
        query.setRefundMethod(refundMethod);
        return ApiResponse.success(refundService.list(query));
    }
}
