package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import com.abc123.hsp.service.PaymentIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付交易异常中心控制器。
 */
@RestController
@RequestMapping("/api/payment-issues")
public class PaymentIssueController {

    private final PaymentIssueService paymentIssueService;

    public PaymentIssueController(PaymentIssueService paymentIssueService) {
        this.paymentIssueService = paymentIssueService;
    }

    /**
     * 查询支付交易异常列表，供运营、研发和测试统一排障使用。
     */
    @GetMapping
    public ApiResponse<PageResultDTO<PaymentIssueRowDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "全部") String issueType,
            @RequestParam(defaultValue = "全部") String severity,
            @RequestParam(required = false) String channelCode,
            @RequestParam(defaultValue = "全部") String paymentMethod,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setIssueType(issueType);
        query.setSeverity(severity);
        query.setChannelCode(channelCode);
        query.setPaymentMethod(paymentMethod);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentIssueService.list(query));
    }
}
