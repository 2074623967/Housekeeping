package com.abc123.hsp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付回调 nonce 到期记录清理任务。
 */
@Component
public class PaymentCallbackNonceCleanupScheduler {

    private final PaymentCallbackSignatureService paymentCallbackSignatureService;
    private final int batchSize;

    @Autowired
    public PaymentCallbackNonceCleanupScheduler(
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            @Value("${payment.callback.nonce-cleanup.batch-size:1000}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("payment callback nonce cleanup batch size must be positive");
        }
        this.paymentCallbackSignatureService = paymentCallbackSignatureService;
        this.batchSize = batchSize;
    }

    /**
     * 单轮只删除有限数量，避免清理事务随历史数据无界增长。
     */
    @Scheduled(fixedDelayString = "${payment.callback.nonce-cleanup.fixed-delay-ms:300000}")
    public void cleanupExpiredNonces() {
        paymentCallbackSignatureService.cleanupExpiredNonces(batchSize);
    }
}
