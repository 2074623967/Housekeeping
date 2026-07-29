package com.abc123.accounting.controller;

import com.abc123.accounting.common.ApiResponse;
import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账务事件控制器。
 */
@RestController
@RequestMapping("/api/accounting/events")
public class AccountingEventController {

    private final AccountingEventService accountingEventService;

    public AccountingEventController(AccountingEventService accountingEventService) {
        this.accountingEventService = accountingEventService;
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
    public ApiResponse<AccountEventDTO> consumePaymentSuccess(@RequestBody PaymentSuccessEventRequestDTO request) {
        return ApiResponse.success(accountingEventService.consumePaymentSuccess(request));
    }

    @PostMapping("/clearing/generated")
    public ApiResponse<AccountEventDTO> consumeClearingGenerated(@RequestBody ClearingGeneratedEventRequestDTO request) {
        return ApiResponse.success(accountingEventService.consumeClearingGenerated(request));
    }
}
