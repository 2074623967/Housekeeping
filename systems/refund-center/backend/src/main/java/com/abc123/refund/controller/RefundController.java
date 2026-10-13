package com.abc123.refund.controller;

import com.abc123.refund.common.ApiResponse;
import com.abc123.refund.common.PaymentProjectionHttpSignature;
import com.abc123.refund.common.RefundCallbackHttpSignature;
import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.service.RefundService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 退款中心后台与内部投影接口。
 */
@RestController
@RequestMapping("/api/refunds")
public class RefundController {

    private final RefundService refundService;
    private final ObjectMapper objectMapper;
    private final String paymentProjectionSecret;
    private final String refundCallbackSecret;

    public RefundController(RefundService refundService, ObjectMapper objectMapper,
            @Value("${HSP_PAYMENT_REFUND_PROJECTION_HTTP_SECRET:}") String paymentProjectionSecret,
            @Value("${HSP_REFUND_CALLBACK_HTTP_SECRET:}") String refundCallbackSecret) {
        this.refundService = refundService;
        this.objectMapper = objectMapper;
        this.paymentProjectionSecret = paymentProjectionSecret;
        this.refundCallbackSecret = refundCallbackSecret;
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

    @GetMapping("/outbox")
    public ApiResponse<PageResultDTO<RefundOutboxItemDTO>> listOutbox(
            @RequestParam(required = false) String eventId,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
        query.setEventId(eventId);
        query.setAggregateId(aggregateId);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(refundService.listOutbox(query));
    }

    @GetMapping("/{refundOrderId}")
    public ApiResponse<RefundDetailDTO> detail(@PathVariable String refundOrderId) {
        return ApiResponse.success(refundService.detail(refundOrderId));
    }

    @GetMapping(value = "/{refundOrderId}/export")
    public ResponseEntity<byte[]> exportDetail(@PathVariable String refundOrderId) {
        byte[] body = refundService.exportDetailSnapshot(refundOrderId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=refund-detail-" + refundOrderId + ".csv")
                .body(body);
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
            @RequestBody String body,
            @RequestHeader(value = RefundCallbackHttpSignature.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = RefundCallbackHttpSignature.SIGNATURE_HEADER, required = false) String signature) {
        if (!RefundCallbackHttpSignature.matches(refundOrderId, body, timestamp, signature, refundCallbackSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "退款回调签名校验失败");
        }
        try {
            RefundCallbackRequestDTO request = objectMapper.readValue(body, RefundCallbackRequestDTO.class);
            if (request != null) {
                request.setRefundOrderId(refundOrderId);
            }
            return ApiResponse.success(refundService.callback(request));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "退款回调载荷无效", exception);
        }
    }

    @PostMapping("/{refundOrderId}/retry")
    public ApiResponse<RefundListItemDTO> retry(@PathVariable String refundOrderId,
                                                 @RequestBody(required = false) RefundActionRequestDTO request) {
        RefundActionRequestDTO action = request == null ? new RefundActionRequestDTO() : request;
        action.setRefundOrderId(refundOrderId);
        return ApiResponse.success(refundService.retry(action));
    }

    @PostMapping("/outbox/{eventId}/dispatch")
    public ApiResponse<RefundOutboxItemDTO> dispatchOutbox(@PathVariable String eventId,
                                                           @RequestBody(required = false) RefundOutboxDispatchRequestDTO request) {
        return ApiResponse.success(refundService.dispatchOutbox(
                eventId,
                request == null ? new RefundOutboxDispatchRequestDTO() : request));
    }

    /**
     * payment-core 或消息消费者使用该接口投影支付成功事实。
     */
    @PostMapping("/internal/payment-success")
    public ApiResponse<Map<String, String>> projectPaymentSuccess(@RequestBody String body,
            @RequestHeader(value = PaymentProjectionHttpSignature.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = PaymentProjectionHttpSignature.SIGNATURE_HEADER, required = false) String signature) {
        if (!PaymentProjectionHttpSignature.matches(body, timestamp, signature, paymentProjectionSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "支付成功投影签名校验失败");
        }
        try {
            PaymentSuccessProjectionDTO projection = objectMapper.readValue(body, PaymentSuccessProjectionDTO.class);
            refundService.projectPaymentSuccess(projection);
            return ApiResponse.success(Collections.singletonMap("paymentOrderId", projection.getPaymentOrderId().trim()));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "支付成功投影载荷无效", exception);
        }
    }
}
