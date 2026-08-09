package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.service.PaymentRequestService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
     * 查询支付请求总览，供运营、研发和测试先看全局态势再下钻列表。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentRequestOverviewDTO> overview(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String terminal,
            @RequestParam(required = false) String clientIp,
            @RequestParam(defaultValue = "全部") String requestStatus,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(requestNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setChannelCode(channelCode);
        query.setTerminal(terminal);
        query.setClientIp(clientIp);
        query.setRequestStatus(requestStatus);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        return ApiResponse.success(paymentRequestService.overview(query));
    }

    /**
     * 查询支付请求列表，供研发、运营和测试排查渠道请求过程。
     */
    @GetMapping
    public ApiResponse<PageResultDTO<PaymentRequestListItemDTO>> list(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String terminal,
            @RequestParam(required = false) String clientIp,
            @RequestParam(defaultValue = "全部") String requestStatus,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(requestNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setChannelCode(channelCode);
        query.setTerminal(terminal);
        query.setClientIp(clientIp);
        query.setRequestStatus(requestStatus);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(paymentRequestService.list(query));
    }

    /**
     * 导出支付请求列表，输出当前筛选条件下的 CSV 快照。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String requestNo,
            @RequestParam(required = false) String paymentOrderId,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) String terminal,
            @RequestParam(required = false) String clientIp,
            @RequestParam(defaultValue = "全部") String requestStatus,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(requestNo);
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setChannelCode(channelCode);
        query.setTerminal(terminal);
        query.setClientIp(clientIp);
        query.setRequestStatus(requestStatus);
        query.setSortField(sortField);
        query.setSortOrder(sortOrder);
        byte[] csvBytes = paymentRequestService.exportCsv(query).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-requests.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }
}
