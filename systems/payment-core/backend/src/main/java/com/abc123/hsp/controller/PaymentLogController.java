package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.service.PaymentLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<PageResultDTO<PaymentLogListItemDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String processStage,
            @RequestParam(defaultValue = "全部") String logLevel,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setProcessStage(processStage);
        query.setLogLevel(logLevel);
        query.setSource(source);
        query.setKeyword(keyword);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentLogService.list(query));
    }
}
