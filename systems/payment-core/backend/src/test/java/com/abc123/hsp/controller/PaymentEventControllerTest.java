package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentEventOverviewDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.service.PaymentEventService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * 支付事件出站控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventControllerTest {

    @Mock
    private PaymentEventService paymentEventService;

    @Test
    void shouldExportPaymentEvents() {
        PaymentEventController controller = new PaymentEventController(paymentEventService);
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId("PAY-001");
        query.setEventType("PAYMENT_SUCCESS");
        query.setPublishStatus("FAILED_OR_DEAD_LETTER");
        query.setDownstreamSystem("accounting-system");
        query.setEventTopic("payment.trade");
        query.setSortField("createdAt");
        query.setSortOrder("desc");
        when(paymentEventService.exportCsv(query)).thenReturn("event-csv");

        ResponseEntity<byte[]> response = controller.export(
                "PAY-001",
                "PAYMENT_SUCCESS",
                "FAILED_OR_DEAD_LETTER",
                "accounting-system",
                "payment.trade",
                "createdAt",
                "desc"
        );

        verify(paymentEventService).exportCsv(query);
        assertEquals("attachment; filename=payment-events.csv", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertArrayEquals("event-csv".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void shouldListPaymentEvents() {
        PaymentEventController controller = new PaymentEventController(paymentEventService);

        controller.list(null, "全部", "全部", "全部", null, "createdAt", "desc", 1, 20);

        verify(paymentEventService).list(any(PaymentEventQueryDTO.class));
    }

    @Test
    void shouldReturnPaymentEventOverview() {
        PaymentEventController controller = new PaymentEventController(paymentEventService);
        PaymentEventOverviewDTO overviewDTO = new PaymentEventOverviewDTO();
        overviewDTO.setTotalEventCount(12L);
        when(paymentEventService.overview(any(PaymentEventQueryDTO.class))).thenReturn(overviewDTO);

        controller.overview("PAY-001", "PAYMENT_SUCCESS", "FAILED_OR_DEAD_LETTER", "accounting-system", "payment.trade", "createdAt", "desc");

        verify(paymentEventService).overview(any(PaymentEventQueryDTO.class));
    }
}
