package com.abc123.hsp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付回调 nonce 清理调度测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentCallbackNonceCleanupSchedulerTest {

    @Mock
    private PaymentCallbackSignatureService paymentCallbackSignatureService;

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;

    @Test
    void shouldDelegateBoundedCleanup() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"),
                anyString(),
                org.mockito.ArgumentMatchers.eq(120))).thenReturn(1);
        new PaymentCallbackNonceCleanupScheduler(
                paymentCallbackSignatureService, paymentTaskCenterMapper, 1000)
                .cleanupExpiredNonces();

        verify(paymentCallbackSignatureService).cleanupExpiredNonces(1000);
        verify(paymentTaskCenterMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"), anyString());
    }

    @Test
    void shouldSkipCleanupWhenLeaseIsHeld() {
        new PaymentCallbackNonceCleanupScheduler(
                paymentCallbackSignatureService, paymentTaskCenterMapper, 1000)
                .cleanupExpiredNonces();

        verify(paymentTaskCenterMapper).initTaskLease("PAYMENT_CALLBACK_NONCE_CLEANUP");
        verify(paymentCallbackSignatureService, never()).cleanupExpiredNonces(1000);
        verify(paymentTaskCenterMapper, never()).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"), anyString());
    }

    @Test
    void shouldReleaseSameLeaseOwnerWhenCleanupFails() {
        when(paymentTaskCenterMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"),
                anyString(),
                org.mockito.ArgumentMatchers.eq(120))).thenReturn(1);
        doThrow(new IllegalStateException("cleanup failed"))
                .when(paymentCallbackSignatureService).cleanupExpiredNonces(1000);
        PaymentCallbackNonceCleanupScheduler scheduler = new PaymentCallbackNonceCleanupScheduler(
                paymentCallbackSignatureService, paymentTaskCenterMapper, 1000);

        assertThrows(IllegalStateException.class, scheduler::cleanupExpiredNonces);
        ArgumentCaptor<String> leaseOwnerCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentTaskCenterMapper).acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"),
                leaseOwnerCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(120));
        verify(paymentTaskCenterMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_CALLBACK_NONCE_CLEANUP"),
                leaseOwnerCaptor.capture());
        assertEquals(leaseOwnerCaptor.getAllValues().get(0), leaseOwnerCaptor.getAllValues().get(1));
    }

    @Test
    void shouldRejectNonPositiveBatchSize() {
        IllegalArgumentException zeroException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackNonceCleanupScheduler(
                        paymentCallbackSignatureService, paymentTaskCenterMapper, 0));
        IllegalArgumentException negativeException = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentCallbackNonceCleanupScheduler(
                        paymentCallbackSignatureService, paymentTaskCenterMapper, -1));

        assertEquals("payment callback nonce cleanup batch size must be positive", zeroException.getMessage());
        assertEquals("payment callback nonce cleanup batch size must be positive", negativeException.getMessage());
    }
}
