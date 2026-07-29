package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.service.PaymentService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 支付单控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Test
    void shouldExportPayments() {
        PaymentController controller = new PaymentController(paymentService);
        when(paymentService.exportCsv(any(PaymentListQueryDTO.class))).thenReturn("payment-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001",
                "ORD-001",
                "微信支付",
                "SUCCESS",
                1,
                20
        );

        verify(paymentService).exportCsv(any(PaymentListQueryDTO.class));
        assertEquals("attachment; filename=payments.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("payment-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListPayments() {
        PaymentController controller = new PaymentController(paymentService);

        controller.list(null, null, "全部", "全部", 1, 20);

        verify(paymentService).list(any(PaymentListQueryDTO.class));
    }
}
