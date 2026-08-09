package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.service.PaymentDayEndService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付日终处理控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentDayEndControllerTest {

    @Mock
    private PaymentDayEndService paymentDayEndService;

    @Test
    void shouldReturnPaymentDayEndOverview() {
        PaymentDayEndController controller = new PaymentDayEndController(paymentDayEndService);
        PaymentDayEndOverviewDTO overviewDTO = new PaymentDayEndOverviewDTO();
        overviewDTO.setLatestBizDate("2026-08-02");
        when(paymentDayEndService.overview()).thenReturn(overviewDTO);

        assertEquals("2026-08-02", controller.overview().getData().getLatestBizDate());
        verify(paymentDayEndService).overview();
    }

    @Test
    void shouldRunPaymentDayEndBatch() {
        PaymentDayEndController controller = new PaymentDayEndController(paymentDayEndService);
        PaymentDayEndOverviewDTO overviewDTO = new PaymentDayEndOverviewDTO();
        overviewDTO.setLatestBatchStatus("已完成");
        when(paymentDayEndService.run(org.mockito.ArgumentMatchers.any(PaymentDayEndRunRequestDTO.class)))
                .thenReturn(overviewDTO);
        PaymentDayEndRunRequestDTO requestDTO = new PaymentDayEndRunRequestDTO();
        requestDTO.setBizDate("2026-08-02");
        requestDTO.setTriggeredBy("finance-ops");
        requestDTO.setRunMode("MANUAL");

        assertEquals("已完成", controller.run(requestDTO).getData().getLatestBatchStatus());
        ArgumentCaptor<PaymentDayEndRunRequestDTO> requestCaptor =
                ArgumentCaptor.forClass(PaymentDayEndRunRequestDTO.class);
        verify(paymentDayEndService).run(requestCaptor.capture());
        assertEquals("2026-08-02", requestCaptor.getValue().getBizDate());
        assertEquals("finance-ops", requestCaptor.getValue().getTriggeredBy());
        assertEquals("MANUAL", requestCaptor.getValue().getRunMode());
    }

    @Test
    void shouldExportPaymentDayEndBatches() {
        PaymentDayEndController controller = new PaymentDayEndController(paymentDayEndService);
        when(paymentDayEndService.exportBatchesCsv()).thenReturn("day-end-csv");

        String body = new String(controller.export().getBody(), java.nio.charset.StandardCharsets.UTF_8);

        verify(paymentDayEndService).exportBatchesCsv();
        assertTrue(body.contains("day-end-csv"));
    }
}
