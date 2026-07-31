package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.service.PaymentFlowService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 支付流水控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentFlowControllerTest {

    @Mock
    private PaymentFlowService paymentFlowService;

    @Test
    void shouldExportPaymentFlows() {
        PaymentFlowController controller = new PaymentFlowController(paymentFlowService);
        when(paymentFlowService.exportCsv(any(PaymentFlowQueryDTO.class))).thenReturn("flow-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001", "ORD-001", "支付尝试", "wx_h5", "H5", "WAIT_CALLBACK", "原始报文", "retryCount", "asc");

        ArgumentCaptor<PaymentFlowQueryDTO> queryCaptor = ArgumentCaptor.forClass(PaymentFlowQueryDTO.class);
        verify(paymentFlowService).exportCsv(queryCaptor.capture());
        PaymentFlowQueryDTO query = queryCaptor.getValue();

        assertNotNull(query);
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("支付尝试", query.getFlowType());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        assertEquals("原始报文", query.getKeyword());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals("attachment; filename=payment-flows.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals(new MediaType("text", "csv", StandardCharsets.UTF_8), response.getHeaders().getContentType());
        assertArrayEquals("flow-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListPaymentFlows() {
        PaymentFlowController controller = new PaymentFlowController(paymentFlowService);

        controller.list(null, null, "全部", null, "全部", null, null, "createdAt", "desc", 1, 20);

        verify(paymentFlowService).list(any(PaymentFlowQueryDTO.class));
    }
}
