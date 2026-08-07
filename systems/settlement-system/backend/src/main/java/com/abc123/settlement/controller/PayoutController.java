package com.abc123.settlement.controller;

import com.abc123.settlement.common.ApiResponse;
import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.abc123.settlement.service.PayoutService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 出款控制器。
 */
@RestController
@RequestMapping("/api/settlements/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @GetMapping
    public ApiResponse<PageResultDTO<PayoutBatchDTO>> list(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String payoutStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(payoutService.list(batchNo, payoutStatus, pageNo, pageSize));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportBatches(
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String payoutStatus) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=settlement-payout-batches.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(payoutService.exportBatches(batchNo, payoutStatus));
    }

    @PostMapping
    public ApiResponse<PayoutBatchDTO> create(@RequestBody CreatePayoutBatchRequestDTO request) {
        return ApiResponse.success(payoutService.create(request));
    }

    @PostMapping("/{payoutBatchNo}/execute")
    public ApiResponse<PayoutBatchDTO> execute(@PathVariable String payoutBatchNo, @RequestBody ExecutePayoutBatchRequestDTO request) {
        return ApiResponse.success(payoutService.execute(payoutBatchNo, request));
    }

    @PostMapping("/{payoutBatchNo}/retry")
    public ApiResponse<PayoutBatchDTO> retry(@PathVariable String payoutBatchNo, @RequestBody RetryPayoutBatchRequestDTO request) {
        return ApiResponse.success(payoutService.retry(payoutBatchNo, request));
    }

    @GetMapping("/{payoutBatchNo}/records")
    public ApiResponse<PageResultDTO<PayoutRecordDTO>> records(
            @PathVariable String payoutBatchNo,
            @RequestParam(required = false) String payoutStatus,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(payoutService.records(payoutBatchNo, payoutStatus, pageNo, pageSize));
    }

    @GetMapping(value = "/{payoutBatchNo}/records/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportRecords(
            @PathVariable String payoutBatchNo,
            @RequestParam(required = false) String payoutStatus) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=settlement-payout-records.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(payoutService.exportRecords(payoutBatchNo, payoutStatus));
    }
}
