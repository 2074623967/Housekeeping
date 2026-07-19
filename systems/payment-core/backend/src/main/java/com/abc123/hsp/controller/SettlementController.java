package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.service.SettlementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping("/workers")
    public ApiResponse<PageResultDTO<WorkerSettlementListItemDTO>> workerList(
            @RequestParam(required = false) String settlementOrderId,
            @RequestParam(required = false) String workerKeyword,
            @RequestParam(defaultValue = "全部") String settlementStatus,
            @RequestParam(defaultValue = "全部") String payoutStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(settlementOrderId);
        query.setWorkerKeyword(workerKeyword);
        query.setSettlementStatus(settlementStatus);
        query.setPayoutStatus(payoutStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(settlementService.workerList(query));
    }
}
