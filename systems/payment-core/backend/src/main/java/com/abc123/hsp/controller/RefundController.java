package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.service.RefundService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ApiResponse<PageResultDTO<RefundListItemDTO>> list(
            @RequestParam(required = false) String refundOrderId,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String refundStatus,
            @RequestParam(defaultValue = "全部") String refundMethod,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId(refundOrderId);
        query.setPaymentOrderId(paymentOrderId);
        query.setRefundStatus(refundStatus);
        query.setRefundMethod(refundMethod);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(refundService.list(query));
    }

    /**
     * 查询退款详情。
     */
    @GetMapping("/{refundOrderId}")
    public ApiResponse<RefundDetailDTO> detail(@PathVariable String refundOrderId) {
        return ApiResponse.success(refundService.detail(refundOrderId));
    }

    /**
     * 后台发起退款申请。
     */
    @PostMapping("/apply")
    public ApiResponse<RefundListItemDTO> apply(@RequestBody RefundApplyRequestDTO request) {
        return ApiResponse.success(refundService.apply(request));
    }

    /**
     * 审核通过退款单并提交处理。
     */
    @PostMapping("/approve")
    public ApiResponse<RefundListItemDTO> approve(@RequestBody RefundActionRequestDTO request) {
        return ApiResponse.success(refundService.approve(request));
    }

    /**
     * 模拟渠道退款成功回调。
     */
    @PostMapping("/success")
    public ApiResponse<RefundListItemDTO> markSuccess(@RequestBody RefundActionRequestDTO request) {
        return ApiResponse.success(refundService.markSuccess(request));
    }

    /**
     * 模拟渠道退款失败回调。
     */
    @PostMapping("/fail")
    public ApiResponse<RefundListItemDTO> markFail(@RequestBody RefundActionRequestDTO request) {
        return ApiResponse.success(refundService.markFail(request));
    }

    /**
     * 失败退款单重新提交渠道处理。
     */
    @PostMapping("/retry")
    public ApiResponse<RefundListItemDTO> retry(@RequestBody RefundActionRequestDTO request) {
        return ApiResponse.success(refundService.retry(request));
    }
}
