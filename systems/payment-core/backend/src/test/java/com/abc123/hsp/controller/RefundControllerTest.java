package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.service.RefundService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    @Mock
    private RefundService refundService;

    @Test
    void shouldExportRefunds() {
        RefundController controller = new RefundController(refundService);
        when(refundService.exportCsv(any(RefundQueryDTO.class))).thenReturn("refund-csv");

        ResponseEntity<byte[]> response = controller.export("REF-001", "PAY-001", "SUCCESS", "原路退款");

        ArgumentCaptor<RefundQueryDTO> queryCaptor = ArgumentCaptor.forClass(RefundQueryDTO.class);
        verify(refundService).exportCsv(queryCaptor.capture());
        RefundQueryDTO query = queryCaptor.getValue();

        assertNotNull(query);
        assertEquals("REF-001", query.getRefundOrderId());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("SUCCESS", query.getRefundStatus());
        assertEquals("原路退款", query.getRefundMethod());
        assertEquals("attachment; filename=refunds.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertArrayEquals("refund-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListRefunds() {
        RefundController controller = new RefundController(refundService);

        controller.list(null, null, "全部", "全部", 1, 20);

        verify(refundService).list(any(RefundQueryDTO.class));
    }
}
