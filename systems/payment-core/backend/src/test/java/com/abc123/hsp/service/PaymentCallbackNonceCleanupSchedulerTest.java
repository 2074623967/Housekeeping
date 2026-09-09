package com.abc123.hsp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付回调 nonce 清理调度测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackNonceCleanupSchedulerTest {

    @Mock
    private PaymentCallbackSignatureService paymentCallbackSignatureService;

    @Test
    void shouldDelegateBoundedCleanup() {
        new PaymentCallbackNonceCleanupScheduler(paymentCallbackSignatureService, 1000)
                .cleanupExpiredNonces();

        verify(paymentCallbackSignatureService).cleanupExpiredNonces(1000);
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackNonceCleanupScheduler(paymentCallbackSignatureService, 0));
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackNonceCleanupScheduler(paymentCallbackSignatureService, -1));

        assertEquals("payment callback nonce cleanup batch size must be positive", zeroException.getMessage());
        assertEquals("payment callback nonce cleanup batch size must be positive", negativeException.getMessage());
    }
}
