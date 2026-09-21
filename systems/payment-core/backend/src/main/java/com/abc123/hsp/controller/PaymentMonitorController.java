package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import com.abc123.hsp.dto.PaymentMonitorOverviewDTO;
import com.abc123.hsp.service.PaymentMonitorService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付监控分析控制器。
 */
@RestController
@RequestMapping("/api/payment-monitor")
public class PaymentMonitorController {

    private final PaymentMonitorService paymentMonitorService;

    public PaymentMonitorController(PaymentMonitorService paymentMonitorService) {
        this.paymentMonitorService = paymentMonitorService;
    }

    /**
     * 查询支付趋势、渠道表现和异常告警。
     */
    @GetMapping("/overview")
    public ApiResponse<PaymentMonitorOverviewDTO> overview() {
        return ApiResponse.success(paymentMonitorService.overview());
    }

    /**
     * 导出当前支付监控快照。
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-monitor-snapshot.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(paymentMonitorService.exportCsv().getBytes(StandardCharsets.UTF_8));
    }
}
