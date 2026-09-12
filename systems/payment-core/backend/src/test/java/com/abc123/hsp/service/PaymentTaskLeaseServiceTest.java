package com.abc123.hsp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
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
    private PlatformTransactionManager transactionManager;
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
    void shouldStopRenewalAndRetainLeaseUntilExpiry() {
        executeTransactionCallbacks();
        when(paymentTaskCenterMapper.acquireTaskLease("ALERT_PROVIDER_PROBE", "owner-a", 630))
                .thenReturn(1);
        doReturn(renewalFuture).when(renewalExecutor).scheduleWithFixedDelay(
                any(Runnable.class), eq(210000L), eq(210000L), eq(TimeUnit.MILLISECONDS));
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(
                paymentTaskCenterMapper, transactionTemplate, renewalExecutor);

        PaymentTaskLeaseService.Lease lease = service.tryAcquire(
                "ALERT_PROVIDER_PROBE", "owner-a", 630);
        lease.retainUntilExpiry();
        lease.retainUntilExpiry();
        lease.close();

        verify(renewalFuture, times(1)).cancel(false);
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

    @Test
    void shouldRedactLeaseOperationFailureFromServiceAndTransactionLogs() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(paymentTaskCenterMapper.acquireTaskLease("PAYMENT_EVENT_RETRY", "owner-secret", 120)).thenReturn(1);
        when(paymentTaskCenterMapper.renewTaskLease("PAYMENT_EVENT_RETRY", "owner-secret", 120))
                .thenThrow(new IllegalStateException(
                        "jdbc:mysql://internal/payment password=lease-secret\ninvalid SQL"));
        doReturn(renewalFuture).when(renewalExecutor).scheduleWithFixedDelay(
                any(Runnable.class), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));
        TransactionTemplate realTransactionTemplate = new TransactionTemplate(transactionManager);
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(
                paymentTaskCenterMapper, realTransactionTemplate, renewalExecutor);
        Logger serviceLogger = (Logger) org.slf4j.LoggerFactory.getLogger(PaymentTaskLeaseService.class);
        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> serviceAppender = new ListAppender<>();
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        serviceAppender.start();
        transactionAppender.start();
        serviceLogger.addAppender(serviceAppender);
        transactionLogger.addAppender(transactionAppender);
        try {
            PaymentTaskLeaseService.Lease lease = service.tryAcquire(
                    "PAYMENT_EVENT_RETRY", "owner-secret", 120);
            ArgumentCaptor<Runnable> renewalCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(renewalExecutor).scheduleWithFixedDelay(
                    renewalCaptor.capture(), eq(40000L), eq(40000L), eq(TimeUnit.MILLISECONDS));

            renewalCaptor.getValue().run();

            assertEquals(1, serviceAppender.list.size());
            assertTrue(serviceAppender.list.get(0).getFormattedMessage()
                    .contains(IllegalStateException.class.getName()));
            assertFalse(serviceAppender.list.get(0).getFormattedMessage().contains("lease-secret"));
            assertNull(serviceAppender.list.get(0).getThrowableProxy());
            boolean transactionFailureCaptured = false;
            for (ILoggingEvent event : transactionAppender.list) {
                assertFalse(event.getFormattedMessage().contains("lease-secret"));
                if (event.getThrowableProxy() != null) {
                    transactionFailureCaptured = true;
                    assertEquals("PAYMENT_TASK_LEASE_OPERATION_FAILED:IllegalStateException",
                            event.getThrowableProxy().getMessage());
                    assertNull(event.getThrowableProxy().getCause());
                    assertEquals(0, event.getThrowableProxy().getStackTraceElementProxyArray().length);
                }
            }
            assertTrue(transactionFailureCaptured);
            verify(transactionManager).rollback(transactionStatus);
            assertNotNull(lease);
        } finally {
            serviceLogger.detachAppender(serviceAppender);
            serviceAppender.stop();
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }
    }

    @Test
    void shouldSanitizeLeaseFailureWithoutTransactionTemplate() {
        when(paymentTaskCenterMapper.initTaskLease("PAYMENT_EVENT_RETRY"))
                .thenThrow(new IllegalArgumentException("password=direct-secret"));
        PaymentTaskLeaseService service = new PaymentTaskLeaseService(paymentTaskCenterMapper);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.tryAcquire("PAYMENT_EVENT_RETRY", "owner-a", 120));

        assertEquals("PAYMENT_TASK_LEASE_OPERATION_FAILED:IllegalArgumentException", exception.getMessage());
        assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
    }

    private void executeTransactionCallbacks() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
    }
}
