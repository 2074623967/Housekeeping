package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.service.PaymentRecordService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 收款记录控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRecordControllerTest {

    @Mock
    private PaymentRecordService paymentRecordService;

    @Test
    void shouldListPaymentRecords() {
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        controller.list("ALL", null, "张女士", "ORD-001", "消费支付", "支付成功", "wx_jsapi", "createdAt", "desc", 1, 20);

        verify(paymentRecordService).list(argThat(query -> "张女士".equals(query.getUserId())));
    }

    @Test
    void shouldKeepLegacyCustomerNameFilterAndRejectAmbiguousParameters() {
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        controller.list("ALL", "李女士", null, null, null, "全部", null, "createdAt", "desc", 1, 20);
        verify(paymentRecordService).list(argThat(query -> "李女士".equals(query.getUserId())));
        assertThrows(IllegalArgumentException.class, () -> controller.list(
                "ALL", "李女士", "张女士", null, null, "全部", null, "createdAt", "desc", 1, 20));
    }

    @Test
    void shouldReturnPaymentRecordDetail() {
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        controller.detail("PAY-001");

        verify(paymentRecordService).detail("PAY-001");
    }

    @Test
    void shouldExportPaymentRecordsAsCsvAttachment() {
        when(paymentRecordService.exportCsv(any(PaymentRecordQueryDTO.class)))
                .thenReturn("\uFEFF序号,支付单号\n");
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        ResponseEntity<byte[]> response = controller.export(
                "ALL", null, "张女士", "ORD-001", "消费支付", "支付成功",
                "wx_jsapi", "createdAt", "desc");

        assertEquals("attachment; filename=payment-records.csv",
                response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertArrayEquals("\uFEFF序号,支付单号\n".getBytes(StandardCharsets.UTF_8), response.getBody());
        verify(paymentRecordService).exportCsv(argThat(query -> "张女士".equals(query.getUserId())));
    }

    @Test
    void shouldRejectAmbiguousExportFiltersBeforeCallingService() {
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        assertThrows(IllegalArgumentException.class, () -> controller.export(
                "ALL", "李女士", "张女士", null, null, "全部", null, "createdAt", "desc"));
        verifyNoInteractions(paymentRecordService);
    }
}
