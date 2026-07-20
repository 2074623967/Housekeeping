package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付路由执行结果控制器。
 */
@RestController
@RequestMapping("/api/payment-routes")
public class PaymentRouteExecutionController {

    private final PaymentRouteExecutionService paymentRouteExecutionService;

    public PaymentRouteExecutionController(PaymentRouteExecutionService paymentRouteExecutionService) {
        this.paymentRouteExecutionService = paymentRouteExecutionService;
    }

    /**
     * 查询支付路由执行结果，供运营、研发和测试统一排查路由命中情况。
     */
    @GetMapping
    public ApiResponse<PageResultDTO<PaymentRouteExecutionListItemDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String routeRule,
            @RequestParam(required = false) String channelCode,
            @RequestParam(defaultValue = "全部") String routeResult,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setRouteRule(routeRule);
        query.setChannelCode(channelCode);
        query.setRouteResult(routeResult);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentRouteExecutionService.list(query));
    }
}
