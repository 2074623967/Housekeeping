package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.service.PaymentDayEndService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付日终处理控制器。
 */
@RestController
@RequestMapping("/api/payment-day-end")
public class PaymentDayEndController {

    private final PaymentDayEndService paymentDayEndService;

    public PaymentDayEndController(PaymentDayEndService paymentDayEndService) {
        this.paymentDayEndService = paymentDayEndService;
    }

    /**
     * 查询支付日终处理总览。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentDayEndOverviewDTO> overview() {
        return ApiResponse.success(paymentDayEndService.overview());
    }

    @GetMapping("/batches/{batchNo}")
    public ApiResponse<PaymentDayEndBatchListItemDTO> detail(@PathVariable String batchNo) {
        return ApiResponse.success(paymentDayEndService.detail(batchNo));
    }

    /**
     * 导出支付日终批次快照。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export() {
        byte[] csvBytes = paymentDayEndService.exportBatchesCsv().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-day-end-batches.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csvBytes);
    }

    /**
     * 手动触发支付日终处理。
     */
    @PostMapping("/run")
    public ApiResponse<PaymentDayEndOverviewDTO> run(@RequestBody PaymentDayEndRunRequestDTO request) {
        return ApiResponse.success(paymentDayEndService.run(request));
    }
}
