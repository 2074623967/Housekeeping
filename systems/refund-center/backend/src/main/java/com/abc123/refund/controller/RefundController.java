package com.abc123.refund.controller;

import com.abc123.refund.common.ApiResponse;
import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.service.RefundService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款中心后台与内部投影接口。
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<RefundListItemDTO>> list(
            @RequestParam(required = false) String refundOrderId,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String refundMethod,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId(refundOrderId);
        query.setPaymentOrderId(paymentOrderId);
        query.setStatus(status);
        query.setRefundMethod(refundMethod);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(refundService.list(query));
    }

    @GetMapping("/overview")
    public ApiResponse<RefundOverviewDTO> overview() {
        return ApiResponse.success(refundService.overview());
    }

    @GetMapping("/{refundOrderId}")
    public ApiResponse<RefundDetailDTO> detail(@PathVariable String refundOrderId) {
        return ApiResponse.success(refundService.detail(refundOrderId));
    }

    @PostMapping("/apply")
    public ApiResponse<RefundListItemDTO> apply(@RequestBody RefundApplyRequestDTO request) {
        return ApiResponse.success(refundService.apply(request));
    }

    @PostMapping("/{refundOrderId}/approve")
    public ApiResponse<RefundListItemDTO> approve(@PathVariable String refundOrderId,
                                                   @RequestBody(required = false) RefundActionRequestDTO request) {
        RefundActionRequestDTO action = request == null ? new RefundActionRequestDTO() : request;
        action.setRefundOrderId(refundOrderId);
        return ApiResponse.success(refundService.approve(action));
    }

    @PostMapping("/{refundOrderId}/submit")
    public ApiResponse<RefundListItemDTO> submit(@PathVariable String refundOrderId,
                                                  @RequestBody(required = false) RefundActionRequestDTO request) {
        RefundActionRequestDTO action = request == null ? new RefundActionRequestDTO() : request;
        action.setRefundOrderId(refundOrderId);
        return ApiResponse.success(refundService.submit(action));
    }

    @PostMapping("/{refundOrderId}/callback")
    public ApiResponse<RefundListItemDTO> callback(@PathVariable String refundOrderId,
                                                    @RequestBody RefundCallbackRequestDTO request) {
        request.setRefundOrderId(refundOrderId);
        return ApiResponse.success(refundService.callback(request));
    }

    @PostMapping("/{refundOrderId}/retry")
    public ApiResponse<RefundListItemDTO> retry(@PathVariable String refundOrderId,
                                                 @RequestBody(required = false) RefundActionRequestDTO request) {
        RefundActionRequestDTO action = request == null ? new RefundActionRequestDTO() : request;
        action.setRefundOrderId(refundOrderId);
        return ApiResponse.success(refundService.retry(action));
    }

    /**
     * payment-core 或消息消费者使用该接口投影支付成功事实。
     */
    @PostMapping("/internal/payment-success")
    public ApiResponse<Void> projectPaymentSuccess(@RequestBody PaymentSuccessProjectionDTO request) {
        refundService.projectPaymentSuccess(request);
        return ApiResponse.success(null);
    }
}

