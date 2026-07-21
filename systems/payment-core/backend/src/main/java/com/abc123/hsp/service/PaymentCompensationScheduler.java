package com.abc123.hsp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付失败补偿调度器。
 *
 * <p>除超时关单外，失败事件重发和失败退款重试也需要由后台统一巡检，
 * 避免完全依赖人工点击造成跨系统状态长时间不一致。</p>
 */
@Component
public class PaymentCompensationScheduler {

    private final PaymentTaskCenterService paymentTaskCenterService;

    public PaymentCompensationScheduler(PaymentTaskCenterService paymentTaskCenterService) {
        this.paymentTaskCenterService = paymentTaskCenterService;
    }

    /**
     * 定期重发失败事件，优先收敛支付成功后的跨系统状态。
     */
    @Scheduled(fixedDelayString = "${payment.event-retry.fixed-delay-ms:300000}")
    public void republishFailedEvents() {
        paymentTaskCenterService.runAutoRepublishFailedEvents();
    }

    /**
     * 定期重试失败退款，减少逆向资金状态长期悬挂。
     */
    @Scheduled(fixedDelayString = "${payment.refund-retry.fixed-delay-ms:300000}")
    public void retryFailedRefunds() {
        paymentTaskCenterService.runAutoRetryFailedRefunds();
    }
}
