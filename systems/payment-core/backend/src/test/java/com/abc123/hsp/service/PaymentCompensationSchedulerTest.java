package com.abc123.hsp.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付失败补偿调度器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompensationSchedulerTest {

    @Mock
    private PaymentTaskCenterService paymentTaskCenterService;

    @Test
    void shouldDelegateToAutoRepublishFailedEvents() {
        new PaymentCompensationScheduler(paymentTaskCenterService).republishFailedEvents();

        verify(paymentTaskCenterService).runAutoRepublishFailedEvents();
    }

    @Test
    void shouldDelegateToAutoRetryFailedRefunds() {
        new PaymentCompensationScheduler(paymentTaskCenterService).retryFailedRefunds();

        verify(paymentTaskCenterService).runAutoRetryFailedRefunds();
    }
}
