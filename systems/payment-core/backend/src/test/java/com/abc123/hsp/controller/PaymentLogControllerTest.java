package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.dto.PaymentLogOverviewDTO;
import com.abc123.hsp.service.PaymentLogService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 支付处理日志控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentLogControllerTest {

    @Mock
    private PaymentLogService paymentLogService;

    @Test
    void shouldExportPaymentLogs() {
        PaymentLogController controller = new PaymentLogController(paymentLogService);
        when(paymentLogService.exportCsv(any(PaymentLogQueryDTO.class))).thenReturn("csv-content");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001",
                "ORD-001",
                "支付提交",
                "INFO",
                "payment-core",
                "回调",
                "createdAt",
                "desc"
        );

        verify(paymentLogService).exportCsv(any(PaymentLogQueryDTO.class));
        assertEquals("attachment; filename=payment-logs.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("csv-content".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListPaymentLogs() {
        PaymentLogController controller = new PaymentLogController(paymentLogService);
        controller.list(null, null, "全部", "全部", null, null, "createdAt", "desc", 1, 20);

        verify(paymentLogService).list(any(PaymentLogQueryDTO.class));
    }

    @Test
    void shouldReturnPaymentLogOverview() {
        PaymentLogController controller = new PaymentLogController(paymentLogService);
        PaymentLogOverviewDTO overviewDTO = new PaymentLogOverviewDTO();
        overviewDTO.setTotalLogCount(16L);
        when(paymentLogService.overview(any(PaymentLogQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("PAY-001", "ORD-001", "渠道回调", "ERROR", "wx_h5", "回调", "createdAt", "desc");

        verify(paymentLogService).overview(any(PaymentLogQueryDTO.class));
    }
}
