package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRecordDetailDTO;
import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.dto.PaymentRecordRowDTO;
import com.abc123.hsp.service.PaymentRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    public ApiResponse<PageResultDTO<PaymentRecordRowDTO>> list(
            @RequestParam(defaultValue = "ALL") String recordType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String businessOrderNo,
            @RequestParam(required = false) String paymentType,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentRecordQueryDTO query = new PaymentRecordQueryDTO();
        query.setRecordType(recordType);
        query.setUserId(userId);
        query.setBusinessOrderNo(businessOrderNo);
        query.setPaymentType(paymentType);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentRecordService.list(query));
    }

    /**
     * 查询单笔支付记录详情，补齐支付尝试、路由、通知与事件轨迹。
     */
    @GetMapping("/{paymentOrderId}")
    public ApiResponse<PaymentRecordDetailDTO> detail(@PathVariable String paymentOrderId) {
        return ApiResponse.success(paymentRecordService.detail(paymentOrderId));
    }
}
