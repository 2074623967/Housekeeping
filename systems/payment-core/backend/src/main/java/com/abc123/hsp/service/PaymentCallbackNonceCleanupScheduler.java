package com.abc123.hsp.service;

import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付回调 nonce 到期记录清理任务。
 */
@Component
public class PaymentCallbackNonceCleanupScheduler {

    private static final String TASK_CODE = "PAYMENT_CALLBACK_NONCE_CLEANUP";
    private static final String LEASE_OWNER_PREFIX = "payment-callback-nonce-cleanup-scheduler:";
    private static final int LEASE_SECONDS = 120;

    private final PaymentCallbackSignatureService paymentCallbackSignatureService;
    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final int batchSize;
    private final String leaseOwner = LEASE_OWNER_PREFIX + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    public PaymentCallbackNonceCleanupScheduler(
            PaymentCallbackSignatureService paymentCallbackSignatureService,
            PaymentTaskCenterMapper paymentTaskCenterMapper,
            @Value("${payment.callback.nonce-cleanup.batch-size:1000}") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("payment callback nonce cleanup batch size must be positive");
        }
        this.paymentCallbackSignatureService = paymentCallbackSignatureService;
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.batchSize = batchSize;
    }

    /**
     * 单轮只删除有限数量，避免清理事务随历史数据无界增长。
     */
    @Scheduled(fixedDelayString = "${payment.callback.nonce-cleanup.fixed-delay-ms:300000}")
    public void cleanupExpiredNonces() {
        paymentTaskCenterMapper.initTaskLease(TASK_CODE);
        if (paymentTaskCenterMapper.acquireTaskLease(TASK_CODE, leaseOwner, LEASE_SECONDS) <= 0) {
            return;
        }
        try {
            paymentCallbackSignatureService.cleanupExpiredNonces(batchSize);
        } finally {
            paymentTaskCenterMapper.releaseTaskLease(TASK_CODE, leaseOwner);
        }
    }
}
