package com.abc123.hsp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentRecordQueryDTO;
import com.abc123.hsp.service.PaymentRecordService;
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

        controller.list("ALL", "USER-001", "ORD-001", "消费支付", "支付成功", "wx_jsapi", "createdAt", "desc", 1, 20);

        verify(paymentRecordService).list(any(PaymentRecordQueryDTO.class));
    }

    @Test
    void shouldReturnPaymentRecordDetail() {
        PaymentRecordController controller = new PaymentRecordController(paymentRecordService);

        controller.detail("PAY-001");

        verify(paymentRecordService).detail("PAY-001");
    }
}
