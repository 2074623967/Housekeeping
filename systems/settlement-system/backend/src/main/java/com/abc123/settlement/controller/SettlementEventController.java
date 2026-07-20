package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.service.SettlementEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    public SettlementEventController(SettlementEventService settlementEventService) {
        this.settlementEventService = settlementEventService;
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
    public ApiResponse<SettlementEventDTO> consumeClearingGenerated(@RequestBody ClearingGeneratedEventRequestDTO request) {
        return ApiResponse.success(settlementEventService.consumeClearingGenerated(request));
    }
}
