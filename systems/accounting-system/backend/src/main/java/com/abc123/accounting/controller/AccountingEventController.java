package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.dto.SettlementCompensationEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
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
 * 账务事件控制器。
 */
@RestController
@RequestMapping("/api/accounting/events")
public class AccountingEventController {

    private static final String REFUND_EVENT_SIGNATURE_HEADER = "X-HSP-Refund-Event-Signature";

    private final AccountingEventService accountingEventService;
    private final ObjectMapper objectMapper;
    private final String paymentEventSecret;
    private final String refundEventSecret;

    public AccountingEventController(AccountingEventService accountingEventService, ObjectMapper objectMapper,
            @Value("${HSP_PAYMENT_SUCCESS_HTTP_SECRET:}") String paymentEventSecret,
            @Value("${HSP_REFUND_SUCCESS_HTTP_SECRET:}") String refundEventSecret) {
        this.accountingEventService = accountingEventService;
        this.objectMapper = objectMapper;
        this.paymentEventSecret = paymentEventSecret;
        this.refundEventSecret = refundEventSecret;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<AccountEventDTO>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(accountingEventService.list(eventType, bizNo, pageNo, pageSize));
    }

    @PostMapping("/payments/success")
    public ResponseEntity<ApiResponse<AccountEventDTO>> consumePaymentSuccess(@RequestBody String body,
            @RequestHeader(value = PaymentEventHttpSignature.HEADER_NAME, required = false) String signature) {
        if (!PaymentEventHttpSignature.matches(body, signature, paymentEventSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("PAYMENT_EVENT_UNAUTHORIZED", "支付事件签名校验失败"));
        }
        try {
            PaymentSuccessEventRequestDTO request = objectMapper.readValue(body, PaymentSuccessEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(accountingEventService.consumePaymentSuccess(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("PAYMENT_EVENT_INVALID", "支付事件载荷无效"));
        }
    }

    @PostMapping("/clearing/generated")
    public ApiResponse<AccountEventDTO> consumeClearingGenerated(@RequestBody ClearingGeneratedEventRequestDTO request) {
        return ApiResponse.success(accountingEventService.consumeClearingGenerated(request));
    }

    @PostMapping("/refunds/success")
    public ResponseEntity<ApiResponse<AccountEventDTO>> consumeRefundSuccess(@RequestBody String body,
            @RequestHeader(value = REFUND_EVENT_SIGNATURE_HEADER, required = false) String signature) {
        if (!PaymentEventHttpSignature.matches(body, signature, refundEventSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("REFUND_EVENT_UNAUTHORIZED", "退款事件签名校验失败"));
        }
        try {
            RefundSuccessEventRequestDTO request = objectMapper.readValue(body, RefundSuccessEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(accountingEventService.consumeRefundSuccess(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("REFUND_EVENT_INVALID", "退款事件载荷无效"));
        }
    }

    @PostMapping("/settlements/compensations")
    public ApiResponse<AccountEventDTO> consumeSettlementCompensation(
            @RequestBody SettlementCompensationEventRequestDTO request) {
        return ApiResponse.success(accountingEventService.consumeSettlementCompensation(request));
    }
}
