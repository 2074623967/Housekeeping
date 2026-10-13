package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.common.PaymentEventHttpSignature;
import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.ClearingReplayResultDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 清分事件控制器。
 */
@RestController
@RequestMapping("/api/clearing/events")
public class ClearingEventController {

    private final ClearingEventService clearingEventService;
    private final ObjectMapper objectMapper;
    private final String paymentEventSecret;

    public ClearingEventController(ClearingEventService clearingEventService, ObjectMapper objectMapper,
            @Value("${HSP_PAYMENT_SUCCESS_HTTP_SECRET:}") String paymentEventSecret) {
        this.clearingEventService = clearingEventService;
        this.objectMapper = objectMapper;
        this.paymentEventSecret = paymentEventSecret;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<ClearingEventDTO>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(clearingEventService.list(eventType, bizNo, pageNo, pageSize));
    }

    @PostMapping("/payments/success")
    public ResponseEntity<ApiResponse<ClearingEventDTO>> consumePaymentSuccess(@RequestBody String body,
            @RequestHeader(value = PaymentEventHttpSignature.HEADER_NAME, required = false) String signature,
            @RequestHeader(value = PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, required = false) String timestamp) {
        if (!PaymentEventHttpSignature.matchesTimed(body, timestamp, signature, paymentEventSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("PAYMENT_EVENT_UNAUTHORIZED", "支付事件签名校验失败"));
        }
        try {
            PaymentSuccessEventRequestDTO request = objectMapper.readValue(body, PaymentSuccessEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(clearingEventService.consumePaymentSuccess(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("PAYMENT_EVENT_INVALID", "支付事件载荷无效"));
        }
    }

    @PostMapping("/replay-failed")
    public ApiResponse<ClearingReplayResultDTO> replayDueFailedEvents() {
        return ApiResponse.success(clearingEventService.replayDueFailedEvents());
    }
}
