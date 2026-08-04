package com.abc123.reconciliation.controller;

import com.abc123.reconciliation.common.ApiResponse;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.service.ReconciliationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对账后台接口。
 */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<ReconciliationOverviewDTO> overview() {
        return ApiResponse.success(service.overview());
    }

    @GetMapping("/batches")
    public ApiResponse<List<ReconciliationBatchListItemDTO>> batches() {
        return ApiResponse.success(service.batches());
    }

    @PostMapping("/batches")
    public ApiResponse<ReconciliationBatchListItemDTO> createBatch(
            @RequestBody ReconciliationBatchCreateRequestDTO request) {
        return ApiResponse.success(service.createBatch(request));
    }

    @PostMapping("/batches/{batchNo}/channel-records")
    public ApiResponse<ReconciliationBatchListItemDTO> addChannelRecord(
            @PathVariable String batchNo, @RequestBody ChannelRecordRequestDTO request) {
        return ApiResponse.success(service.addChannelRecord(batchNo, request));
    }

    @PostMapping("/batches/{batchNo}/internal-records")
    public ApiResponse<ReconciliationBatchListItemDTO> addInternalRecord(
            @PathVariable String batchNo, @RequestBody InternalRecordRequestDTO request) {
        return ApiResponse.success(service.addInternalRecord(batchNo, request));
    }

    @PostMapping("/batches/{batchNo}/run")
    public ApiResponse<ReconciliationBatchListItemDTO> run(@PathVariable String batchNo) {
        return ApiResponse.success(service.run(batchNo));
    }

    @GetMapping("/differences")
    public ApiResponse<PageResultDTO<ReconciliationDifferenceDTO>> differences(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String differenceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        DifferenceQueryDTO query = new DifferenceQueryDTO();
        query.setBatchNo(batchNo);
        query.setDifferenceType(differenceType);
        query.setStatus(status);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(service.differences(query));
    }

    @PostMapping("/differences/{differenceNo}/resolve")
    public ApiResponse<Void> resolve(@PathVariable String differenceNo,
                                     @RequestBody DifferenceResolveRequestDTO request) {
        request.setDifferenceNo(differenceNo);
        service.resolve(request);
        return ApiResponse.success(null);
    }
}

