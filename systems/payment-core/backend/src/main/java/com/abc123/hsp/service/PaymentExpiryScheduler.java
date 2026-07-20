package com.abc123.hsp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时补偿任务。
 *
 * <p>收银台失效后不能依赖用户再次访问页面才能关闭支付单，
 * 因此由后台任务主动收口待支付和支付中的超时单。</p>
 */
@Component
public class PaymentExpiryScheduler {

    private final PaymentExpiryTaskService paymentExpiryTaskService;

    public PaymentExpiryScheduler(PaymentExpiryTaskService paymentExpiryTaskService) {
        this.paymentExpiryTaskService = paymentExpiryTaskService;
    }

    /**
     * 定期关闭已超过收银台失效时间的支付单。
     */
    @Scheduled(fixedDelayString = "${payment.expiry-close.fixed-delay-ms:60000}")
    public void closeExpiredPayments() {
        paymentExpiryTaskService.closeExpiredPayments();
    }
}
