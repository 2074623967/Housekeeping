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
    private final boolean patrolEnabled;

    public PaymentDayEndScheduler(PaymentTaskCenterService paymentTaskCenterService) {
        this(paymentTaskCenterService, false, false);
    }

    PaymentDayEndScheduler(PaymentTaskCenterService paymentTaskCenterService, boolean enabled) {
        this(paymentTaskCenterService, enabled, false);
    }

    @Autowired
    public PaymentDayEndScheduler(
            PaymentTaskCenterService paymentTaskCenterService,
            @Value("${payment.day-end.auto.enabled:false}") boolean enabled,
            @Value("${payment.day-end.patrol.enabled:false}") boolean patrolEnabled) {
        this.paymentTaskCenterService = paymentTaskCenterService;
        this.enabled = enabled;
        this.patrolEnabled = patrolEnabled;
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

    /**
     * 上海凌晨检查前一业务日批次与任务日志；租约忙时由后续小时再次尝试。
     */
    @Scheduled(
            cron = "${payment.day-end.patrol.cron:0 30 2-6 * * *}",
            zone = "Asia/Shanghai")
    public void patrolPreviousBusinessDay() {
        if (patrolEnabled) {
            paymentTaskCenterService.runAutoPaymentDayEndPatrol();
        }
    }
}
