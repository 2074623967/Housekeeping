package com.abc123.clearing.controller;

import com.abc123.clearing.common.ApiResponse;
import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public ClearingEventController(ClearingEventService clearingEventService) {
        this.clearingEventService = clearingEventService;
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
    public ApiResponse<ClearingEventDTO> consumePaymentSuccess(@RequestBody PaymentSuccessEventRequestDTO request) {
        return ApiResponse.success(clearingEventService.consumePaymentSuccess(request));
    }
}
