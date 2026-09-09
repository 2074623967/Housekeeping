package com.abc123.hsp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 自动任务租约管理测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentTaskLeaseServiceTest {

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionStatus transactionStatus;
    @Mock
    private ScheduledExecutorService renewalExecutor;
    @Mock
    private ScheduledFuture<?> renewalFuture;

    @Test
    void shouldAcquireRenewAndReleaseInIndependentTransactions() {
        executeTransactionCallbacks();
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EVENT_RETRY", "owner-a", 120)).thenReturn(1);
        when(paymentTaskCenterMapper.renewTaskLease("PAYMENT_EVENT_RETRY", "owner-a", 120)).thenReturn(1);
        doReturn(renewalFuture).when(renewalExecutor).scheduleWithFixedDelay(
                any(Runnable.class), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(
                paymentTaskCenterMapper, transactionTemplate, renewalExecutor);

        PaymentTaskLeaseService.Lease lease = service.tryAcquire("PAYMENT_EVENT_RETRY", "owner-a", 120);
        assertNotNull(lease);
        ArgumentCaptor<Runnable> renewalCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(renewalExecutor).scheduleWithFixedDelay(
                renewalCaptor.capture(), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));

        renewalCaptor.getValue().run();
        lease.close();
        lease.close();

        verify(paymentTaskCenterMapper).initTaskLease("PAYMENT_EVENT_RETRY");
        verify(paymentTaskCenterMapper).renewTaskLease("PAYMENT_EVENT_RETRY", "owner-a", 120);
        verify(paymentTaskCenterMapper, times(1)).releaseTaskLease("PAYMENT_EVENT_RETRY", "owner-a");
        verify(renewalFuture).cancel(false);
        verify(transactionTemplate, times(3)).execute(any());
    }

    @Test
    void shouldNotStartRenewalWhenLeaseIsHeld() {
        executeTransactionCallbacks();
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(
                paymentTaskCenterMapper, transactionTemplate, renewalExecutor);

        assertNull(service.tryAcquire("PAYMENT_EVENT_RETRY", "owner-b", 120));

        verify(renewalExecutor, never()).scheduleWithFixedDelay(
                any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        verify(paymentTaskCenterMapper, never()).releaseTaskLease(any(String.class), any(String.class));
        verify(transactionTemplate, times(1)).execute(any());
    }

    @Test
    void shouldStopRenewingAfterOwnerLosesLease() {
        executeTransactionCallbacks();
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EVENT_RETRY", "owner-a", 120)).thenReturn(1);
        doReturn(renewalFuture).when(renewalExecutor).scheduleWithFixedDelay(
                any(Runnable.class), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(
                paymentTaskCenterMapper, transactionTemplate, renewalExecutor);

        PaymentTaskLeaseService.Lease lease = service.tryAcquire("PAYMENT_EVENT_RETRY", "owner-a", 120);
        ArgumentCaptor<Runnable> renewalCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(renewalExecutor).scheduleWithFixedDelay(
                renewalCaptor.capture(), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));
        renewalCaptor.getValue().run();
        renewalCaptor.getValue().run();

        verify(paymentTaskCenterMapper, times(1)).renewTaskLease("PAYMENT_EVENT_RETRY", "owner-a", 120);
        verify(paymentTaskCenterMapper, times(1)).releaseTaskLease("PAYMENT_EVENT_RETRY", "owner-a");
        verify(renewalFuture).cancel(false);
        assertNotNull(lease);
    }

    private void executeTransactionCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }
}
