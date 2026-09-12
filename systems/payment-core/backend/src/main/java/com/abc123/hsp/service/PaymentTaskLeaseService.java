package com.abc123.hsp.service;

import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 自动任务数据库租约管理。
 */
@Service
public class PaymentTaskLeaseService implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentTaskLeaseService.class);
    private static final String OPERATION_FAILURE_CODE = "PAYMENT_TASK_LEASE_OPERATION_FAILED";

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final TransactionTemplate leaseTransactionTemplate;
    private final ScheduledExecutorService renewalExecutor;

    @Autowired
    public PaymentTaskLeaseService(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                   PlatformTransactionManager transactionManager) {
        this(paymentTaskCenterMapper, buildTransactionTemplate(transactionManager),
                Executors.newSingleThreadScheduledExecutor(new LeaseRenewalThreadFactory()));
    }

    public PaymentTaskLeaseService(PaymentTaskCenterMapper paymentTaskCenterMapper) {
        this(paymentTaskCenterMapper, null, null);
    }

    PaymentTaskLeaseService(PaymentTaskCenterMapper paymentTaskCenterMapper,
                            TransactionTemplate leaseTransactionTemplate,
                            ScheduledExecutorService renewalExecutor) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.leaseTransactionTemplate = leaseTransactionTemplate;
        this.renewalExecutor = renewalExecutor;
    }

    /**
     * 获取租约并在后台按租期三分之一的间隔续租；未获取时返回 {@code null}。
     */
    public Lease tryAcquire(String taskCode, String lockOwner, int leaseSeconds) {
        if (leaseSeconds <= 0) {
            throw new IllegalArgumentException("payment task lease seconds must be positive");
        }
        int lockedRows = executeInLeaseTransaction(() -> {
            paymentTaskCenterMapper.initTaskLease(taskCode);
            return paymentTaskCenterMapper.acquireTaskLease(taskCode, lockOwner, leaseSeconds);
        });
        if (lockedRows <= 0) {
            return null;
        }
        Lease lease = new Lease(taskCode, lockOwner, leaseSeconds);
        lease.startRenewal();
        return lease;
    }

    @Override
    public void destroy() {
        if (renewalExecutor != null) {
            renewalExecutor.shutdownNow();
        }
    }

    private int renew(String taskCode, String lockOwner, int leaseSeconds) {
        return executeInLeaseTransaction(() -> paymentTaskCenterMapper.renewTaskLease(
                taskCode, lockOwner, leaseSeconds));
    }

    private int release(String taskCode, String lockOwner) {
        return executeInLeaseTransaction(() -> paymentTaskCenterMapper.releaseTaskLease(taskCode, lockOwner));
    }

    private int executeInLeaseTransaction(LeaseOperation operation) {
        if (leaseTransactionTemplate == null) {
            return executeSafely(operation);
        }
        Integer result = leaseTransactionTemplate.execute(status -> executeSafely(operation));
        return result == null ? 0 : result;
    }

    private int executeSafely(LeaseOperation operation) {
        try {
            return operation.execute();
        } catch (RuntimeException exception) {
            throw sanitizeException(exception);
        }
    }

    private RuntimeException sanitizeException(RuntimeException exception) {
        return exception instanceof SanitizedTaskLeaseException
                ? exception
                : new SanitizedTaskLeaseException(exception);
    }

    private String exceptionType(RuntimeException exception) {
        return exception instanceof SanitizedTaskLeaseException
                ? ((SanitizedTaskLeaseException) exception).getOriginalExceptionType()
                : exception.getClass().getName();
    }

    private static TransactionTemplate buildTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }

    @FunctionalInterface
    private interface LeaseOperation {
        int execute();
    }

    public final class Lease implements AutoCloseable {

        private final String taskCode;
        private final String lockOwner;
        private final int leaseSeconds;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private ScheduledFuture<?> renewalFuture;

        private Lease(String taskCode, String lockOwner, int leaseSeconds) {
            this.taskCode = taskCode;
            this.lockOwner = lockOwner;
            this.leaseSeconds = leaseSeconds;
        }

        private void startRenewal() {
            if (renewalExecutor == null) {
                return;
            }
            long renewalIntervalMillis = Math.max(1000L, leaseSeconds * 1000L / 3L);
            renewalFuture = renewalExecutor.scheduleWithFixedDelay(
                    this::renewIfActive,
                    renewalIntervalMillis,
                    renewalIntervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        private void renewIfActive() {
            if (closed.get()) {
                return;
            }
            try {
                if (renew(taskCode, lockOwner, leaseSeconds) <= 0) {
                    close();
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("支付任务租约续期失败, taskCode={}, lockOwner={}, exceptionType={}",
                        taskCode, lockOwner, exceptionType(exception));
            }
        }

        @Override
        public void close() {
            if (!stopRenewal()) {
                return;
            }
            release(taskCode, lockOwner);
        }

        /**
         * 停止续期但保留数据库租约至自然过期，用于需要共享冷却窗口的场景。
         */
        public void retainUntilExpiry() {
            stopRenewal();
        }

        private boolean stopRenewal() {
            if (!closed.compareAndSet(false, true)) {
                return false;
            }
            if (renewalFuture != null) {
                renewalFuture.cancel(false);
            }
            return true;
        }
    }

    private static final class LeaseRenewalThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "payment-task-lease-renewal");
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class SanitizedTaskLeaseException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final String originalExceptionType;

        private SanitizedTaskLeaseException(RuntimeException exception) {
            super(OPERATION_FAILURE_CODE + ":" + exceptionName(exception), null, false, false);
            this.originalExceptionType = exception.getClass().getName();
        }

        private String getOriginalExceptionType() {
            return originalExceptionType;
        }

        private static String exceptionName(RuntimeException exception) {
            String simpleName = exception.getClass().getSimpleName();
            return simpleName.isEmpty() ? RuntimeException.class.getSimpleName() : simpleName;
        }
    }
}
