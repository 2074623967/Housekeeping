package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.service.PaymentFlowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<PageResultDTO<PaymentFlowListItemDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String flowType,
            @RequestParam(required = false) String channelCode,
            @RequestParam(defaultValue = "全部") String terminal,
            @RequestParam(required = false) String businessStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setFlowType(flowType);
        query.setChannelCode(channelCode);
        query.setTerminal(terminal);
        query.setBusinessStatus(businessStatus);
        query.setKeyword(keyword);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentFlowService.list(query));
    }
}
