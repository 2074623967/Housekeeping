package com.abc123.hsp.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付超时补偿任务。
 *
 * <p>收银台失效后不能依赖用户再次访问页面处理未提交支付单，
 * 因此由后台任务关闭没有任何渠道提交事实的超时单。</p>
 */
@Component
public class PaymentExpiryScheduler {

    private final PaymentTaskCenterService paymentTaskCenterService;

    public PaymentExpiryScheduler(PaymentTaskCenterService paymentTaskCenterService) {
        this.paymentTaskCenterService = paymentTaskCenterService;
    }

    /**
     * 定期关闭已超过收银台失效时间且从未提交渠道的支付单。
     */
    @Scheduled(fixedDelayString = "${payment.expiry-close.fixed-delay-ms:60000}")
    public void closeExpiredPayments() {
        paymentTaskCenterService.runAutoCloseExpiredPayments();
    }
}
