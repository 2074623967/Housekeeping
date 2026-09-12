package com.abc123.hsp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付日终自动调度器。
 */
@Component
public class PaymentDayEndScheduler {

    private final PaymentTaskCenterService paymentTaskCenterService;
    private final boolean enabled;

    public PaymentDayEndScheduler(PaymentTaskCenterService paymentTaskCenterService) {
        this(paymentTaskCenterService, false);
    }

    @Autowired
    public PaymentDayEndScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            @Value("${payment.day-end.auto.enabled:false}") boolean enabled) {
        this.paymentTaskCenterService = paymentTaskCenterService;
        this.enabled = enabled;
    }

    /**
     * 上海零点后生成前一已结束业务日快照；默认关闭，需由目标环境显式启用。
     */
    @Scheduled(
            cron = "${payment.day-end.auto.cron:0 30 0 * * *}",
            zone = "Asia/Shanghai")
    public void runPreviousBusinessDay() {
        if (enabled) {
            paymentTaskCenterService.runAutoPaymentDayEnd();
        }
    }
}
