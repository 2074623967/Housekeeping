package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.service.SettlementService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/workers")
    public ApiResponse<List<WorkerSettlementListItemDTO>> workerList() {
        return ApiResponse.success(settlementService.workerList());
    }
}
