package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.common.ClearingEventHttpSignature;
import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.service.SettlementEventService;
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
 * 结算事件控制器。
 */
@RestController
@RequestMapping("/api/settlements/events")
public class SettlementEventController {

    private final SettlementEventService settlementEventService;
    private final ObjectMapper objectMapper;
    private final String clearingEventSecret;

    public SettlementEventController(SettlementEventService settlementEventService, ObjectMapper objectMapper,
            @Value("${HSP_CLEARING_GENERATED_HTTP_SECRET:}") String clearingEventSecret) {
        this.settlementEventService = settlementEventService;
        this.objectMapper = objectMapper;
        this.clearingEventSecret = clearingEventSecret;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<SettlementEventDTO>> list(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String bizNo,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(settlementEventService.list(eventType, bizNo, pageNo, pageSize));
    }

    @PostMapping("/clearing/generated")
    public ResponseEntity<ApiResponse<SettlementEventDTO>> consumeClearingGenerated(@RequestBody String body,
            @RequestHeader(value = ClearingEventHttpSignature.HEADER_NAME, required = false) String signature,
            @RequestHeader(value = ClearingEventHttpSignature.TIMESTAMP_HEADER_NAME, required = false) String timestamp) {
        if (!ClearingEventHttpSignature.matches(body, timestamp, signature, clearingEventSecret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.failure("CLEARING_EVENT_UNAUTHORIZED", "清分事件签名校验失败"));
        }
        try {
            ClearingGeneratedEventRequestDTO request = objectMapper.readValue(body, ClearingGeneratedEventRequestDTO.class);
            return ResponseEntity.ok(ApiResponse.success(settlementEventService.consumeClearingGenerated(request)));
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.failure("CLEARING_EVENT_INVALID", "清分事件载荷无效"));
        }
    }
}
