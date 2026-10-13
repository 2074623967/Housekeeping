package com.abc123.reconciliation.controller;

import com.abc123.reconciliation.common.ApiResponse;
import com.abc123.reconciliation.common.ReconciliationFactSignature;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.BatchQueryDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchDetailDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.dto.CrossSystemChainSnapshotDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;
import com.abc123.reconciliation.service.ReconciliationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 对账后台接口。
 */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;
    private final ObjectMapper objectMapper;

    @Value("${HSP_PAYMENT_RECONCILIATION_FACT_SECRET:}")
    private String paymentFactSecret;
    @Value("${HSP_CLEARING_RECONCILIATION_FACT_SECRET:}")
    private String clearingFactSecret;
    @Value("${HSP_SETTLEMENT_RECONCILIATION_FACT_SECRET:}")
    private String settlementFactSecret;
    @Value("${HSP_ACCOUNTING_RECONCILIATION_FACT_SECRET:}")
    private String accountingFactSecret;
    @Value("${HSP_REFUND_RECONCILIATION_FACT_SECRET:}")
    private String refundFactSecret;

    public ReconciliationController(ReconciliationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/overview")
    public ApiResponse<ReconciliationOverviewDTO> overview() {
        return ApiResponse.success(service.overview());
    }

    @GetMapping("/batches")
    public ApiResponse<PageResultDTO<ReconciliationBatchListItemDTO>> batches(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        BatchQueryDTO query = new BatchQueryDTO();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return ApiResponse.success(service.batches(query));
    }

    @GetMapping("/batches/{batchNo}")
    public ApiResponse<ReconciliationBatchDetailDTO> detail(@PathVariable String batchNo) {
        return ApiResponse.success(service.detail(batchNo));
    }

    @GetMapping("/batches/{batchNo}/export")
    public ResponseEntity<byte[]> exportDetail(@PathVariable String batchNo) {
        byte[] body = service.exportBatchDetail(batchNo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=reconciliation-batch-" + batchNo + ".csv")
                .body(body);
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

    @PostMapping("/batches/{batchNo}/readiness-check")
    public ApiResponse<ReconciliationBatchListItemDTO> checkBatchReadiness(@PathVariable String batchNo) {
        return ApiResponse.success(service.checkBatchReadiness(batchNo));
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

    @PostMapping("/cross-system/facts")
    public ApiResponse<CrossSystemChainSnapshotDTO> recordCrossSystemFact(
            @RequestBody String body,
            @RequestHeader(value = ReconciliationFactSignature.SOURCE_HEADER, required = false) String source,
            @RequestHeader(value = ReconciliationFactSignature.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = ReconciliationFactSignature.SIGNATURE_HEADER, required = false) String signature) {
        if (!ReconciliationFactSignature.matches(body, source, timestamp, signature, factSecret(source))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "对账事实签名无效");
        }
        try {
            CrossSystemFactRequestDTO request = objectMapper.readValue(body, CrossSystemFactRequestDTO.class);
            if (!source.equals(request.getSystemCode())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "对账事实来源不匹配");
            }
            return ApiResponse.success(service.recordCrossSystemFact(request));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账事实格式无效", exception);
        }
    }

    private String factSecret(String source) {
        if ("payment-core".equals(source)) {
            return paymentFactSecret;
        }
        if ("clearing-system".equals(source)) {
            return clearingFactSecret;
        }
        if ("settlement-system".equals(source)) {
            return settlementFactSecret;
        }
        if ("accounting-system".equals(source)) {
            return accountingFactSecret;
        }
        if ("refund-center".equals(source)) {
            return refundFactSecret;
        }
        return null;
    }

    @GetMapping("/cross-system/chains/{businessChainId}")
    public ApiResponse<CrossSystemChainSnapshotDTO> crossSystemChain(
            @PathVariable String businessChainId) {
        return ApiResponse.success(service.crossSystemChain(businessChainId));
    }
}
