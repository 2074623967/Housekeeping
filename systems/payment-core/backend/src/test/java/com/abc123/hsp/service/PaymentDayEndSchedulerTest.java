package com.abc123.hsp.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 支付日终自动调度器单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentDayEndSchedulerTest {

    @Mock
    private PaymentDayEndService paymentDayEndService;

    @Test
    void shouldRunAutomaticDayEndWhenEnabled() {
        new PaymentDayEndScheduler(paymentDayEndService, true).runPreviousBusinessDay();

        verify(paymentDayEndService).runAutomatically();
    }

    @Test
    void shouldSkipAutomaticDayEndByDefault() {
        new PaymentDayEndScheduler(paymentDayEndService).runPreviousBusinessDay();

        verify(paymentDayEndService, never()).runAutomatically();
    }

    @Test
    void shouldScheduleInFixedShanghaiBusinessTimezone() throws NoSuchMethodException {
        Scheduled scheduled = PaymentDayEndScheduler.class
                .getMethod("runPreviousBusinessDay")
                .getAnnotation(Scheduled.class);

        org.junit.jupiter.api.Assertions.assertEquals("Asia/Shanghai", scheduled.zone());
        org.junit.jupiter.api.Assertions.assertEquals(
                "${payment.day-end.auto.cron:0 30 0 * * *}", scheduled.cron());
    }
}
