package com.abc123.hsp.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付超时自动关单任务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentExpirySchedulerTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Test
    void shouldCloseExpiredPaymentAndWriteEvent() {
        ExpiredPaymentDTO payment = new ExpiredPaymentDTO();
        payment.setPaymentOrderId("PAY-EXPIRED");
        payment.setOrderNo("ORD-EXPIRED");
        payment.setChannelTransactionNo("CHANNEL-EXPIRED");
        when(paymentMapper.findExpiredPayments()).thenReturn(Collections.singletonList(payment));

        new PaymentExpiryScheduler(paymentMapper).closeExpiredPayments();

        verify(paymentMapper).updatePaymentStatus(
                "PAY-EXPIRED", "CLOSED", "danger", "CHANNEL-EXPIRED");
        verify(paymentMapper).updatePaymentAttemptStatusByPaymentOrderId(
                "PAY-EXPIRED", "已关闭", "danger");
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-EXPIRED", "已失效", "danger");
        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_EXPIRED_CLOSED"),
                org.mockito.ArgumentMatchers.eq("PAY-EXPIRED"),
                org.mockito.ArgumentMatchers.eq("ORD-EXPIRED"),
                org.mockito.ArgumentMatchers.contains("PREPAY_EXPIRED"));
    }
}
