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
    private static final String REFUND_EVENT_TIMESTAMP_HEADER = "X-HSP-Refund-Event-Timestamp";
    private static final String CLEARING_EVENT_SIGNATURE_HEADER = "X-HSP-Clearing-Event-Signature";
    private static final String SETTLEMENT_COMPENSATION_SIGNATURE_HEADER = "X-HSP-Settlement-Compensation-Signature";

    private final AccountingEventService accountingEventService;
    private final ObjectMapper objectMapper;
    private final String paymentEventSecret;
    private final String refundEventSecret;
    private final String clearingEventSecret;
    private final String settlementCompensationSecret;

    public AccountingEventController(AccountingEventService accountingEventService, ObjectMapper objectMapper,
            @Value("${HSP_PAYMENT_SUCCESS_HTTP_SECRET:}") String paymentEventSecret,
            @Value("${HSP_REFUND_SUCCESS_HTTP_SECRET:}") String refundEventSecret,
            @Value("${HSP_CLEARING_GENERATED_HTTP_SECRET:}") String clearingEventSecret,
            @Value("${HSP_SETTLEMENT_COMPENSATION_HTTP_SECRET:}") String settlementCompensationSecret) {
        this.accountingEventService = accountingEventService;
        this.objectMapper = objectMapper;
        this.paymentEventSecret = paymentEventSecret;
        this.refundEventSecret = refundEventSecret;
        this.clearingEventSecret = clearingEventSecret;
        this.settlementCompensationSecret = settlementCompensationSecret;
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
            @RequestHeader(value = PaymentEventHttpSignature.HEADER_NAME, required = false) String signature,
            @RequestHeader(value = PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, required = false) String timestamp) {
        if (!PaymentEventHttpSignature.matchesTimed(body, timestamp, signature, paymentEventSecret)) {
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
    public ResponseEntity<ApiResponse<AccountEventDTO>> consumeClearingGenerated(@RequestBody String body,
            @RequestHeader(value = CLEARING_EVENT_SIGNATURE_HEADER, required = false) String signature) {
        if (!PaymentEventHttpSignature.matches(body, signature, clearingEventSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("CLEARING_EVENT_UNAUTHORIZED", "清分事件签名校验失败"));
        }
        try {
            ClearingGeneratedEventRequestDTO request = objectMapper.readValue(body, ClearingGeneratedEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(accountingEventService.consumeClearingGenerated(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("CLEARING_EVENT_INVALID", "清分事件载荷无效"));
        }
    }

    @PostMapping("/refunds/success")
    public ResponseEntity<ApiResponse<AccountEventDTO>> consumeRefundSuccess(@RequestBody String body,
            @RequestHeader(value = REFUND_EVENT_SIGNATURE_HEADER, required = false) String signature,
            @RequestHeader(value = REFUND_EVENT_TIMESTAMP_HEADER, required = false) String timestamp) {
        if (!PaymentEventHttpSignature.matchesTimed(body, timestamp, signature, refundEventSecret)) {
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
    public ResponseEntity<ApiResponse<AccountEventDTO>> consumeSettlementCompensation(@RequestBody String body,
            @RequestHeader(value = SETTLEMENT_COMPENSATION_SIGNATURE_HEADER, required = false) String signature) {
        if (!PaymentEventHttpSignature.matches(body, signature, settlementCompensationSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("SETTLEMENT_COMPENSATION_UNAUTHORIZED", "结算补偿签名校验失败"));
        }
        try {
            SettlementCompensationEventRequestDTO request = objectMapper.readValue(
                    body, SettlementCompensationEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(accountingEventService.consumeSettlementCompensation(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("SETTLEMENT_COMPENSATION_INVALID", "结算补偿载荷无效"));
        }
    }
}
