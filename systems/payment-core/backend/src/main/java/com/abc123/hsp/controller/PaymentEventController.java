package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.service.PaymentEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付事件出站控制器。
 */
@RestController
@RequestMapping("/api/payment-events")
public class PaymentEventController {

    private final PaymentEventService paymentEventService;

    public PaymentEventController(PaymentEventService paymentEventService) {
        this.paymentEventService = paymentEventService;
    }

    /**
     * 查询支付事件出站列表。
     */
    @GetMapping
    public ApiResponse<PageResultDTO<PaymentEventListItemDTO>> list(
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String eventType,
            @RequestParam(defaultValue = "全部") String publishStatus,
            @RequestParam(defaultValue = "全部") String downstreamSystem,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setEventType(eventType);
        query.setPublishStatus(publishStatus);
        query.setDownstreamSystem(downstreamSystem);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentEventService.list(query));
    }

    /**
     * 手动重发支付事件。
     */
    @PostMapping("/republish")
    public ApiResponse<PageResultDTO<PaymentEventListItemDTO>> republish(
            @RequestBody PaymentEventRepublishRequestDTO request,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(defaultValue = "全部") String eventType,
            @RequestParam(defaultValue = "全部") String publishStatus,
            @RequestParam(defaultValue = "全部") String downstreamSystem,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setEventType(eventType);
        query.setPublishStatus(publishStatus);
        query.setDownstreamSystem(downstreamSystem);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentEventService.republish(request, query));
    }
}
