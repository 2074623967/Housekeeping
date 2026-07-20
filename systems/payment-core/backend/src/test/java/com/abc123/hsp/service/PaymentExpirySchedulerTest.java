package com.abc123.hsp.service;

import static org.mockito.Mockito.verify;

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
    private PaymentTaskCenterService paymentTaskCenterService;

    @Test
    void shouldDelegateToExpiryTaskService() {
        new PaymentExpiryScheduler(paymentTaskCenterService).closeExpiredPayments();

        verify(paymentTaskCenterService).runAutoCloseExpiredPayments();
    }
}
