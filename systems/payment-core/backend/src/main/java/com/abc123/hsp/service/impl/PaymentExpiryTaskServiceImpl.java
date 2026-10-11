package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.List;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付超时关单任务服务默认实现。
 */
@Service
public class PaymentExpiryTaskServiceImpl implements PaymentExpiryTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentExpiryTaskServiceImpl.class);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 512;
    private static final String FAILURE_CODE = "EXPIRY_CLOSE_FAILED";

    private final PaymentMapper paymentMapper;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;
    private final int retryIntervalSeconds;
    private final int retryBackoffFactor;
    private final int maxRetryIntervalSeconds;

    public PaymentExpiryTaskServiceImpl(PaymentMapper paymentMapper,
                                        PlatformTransactionManager transactionManager,
                                        @Value("${payment.expiry-close.batch-size:100}") int batchSize,
                                        @Value("${payment.expiry-close.retry-interval-seconds:60}")
                                        int retryIntervalSeconds,
                                        @Value("${payment.expiry-close.retry-backoff-factor:2}")
                                        int retryBackoffFactor,
                                        @Value("${payment.expiry-close.max-retry-interval-seconds:3600}")
                                        int maxRetryIntervalSeconds) {
        this.paymentMapper = paymentMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
        this.retryIntervalSeconds = Math.max(retryIntervalSeconds, 1);
        this.retryBackoffFactor = Math.max(retryBackoffFactor, 1);
        this.maxRetryIntervalSeconds = Math.max(maxRetryIntervalSeconds, this.retryIntervalSeconds);
    }

    @Override
    public PaymentExpiryCloseResultDTO closeExpiredPayments() {
        cleanupStaleRetries();
        List<ExpiredPaymentDTO> expiredPayments = safeList(paymentMapper.findExpiredPayments(batchSize));
        PaymentExpiryCloseResultDTO result = new PaymentExpiryCloseResultDTO();
        result.setProcessedCount(expiredPayments.size());
        for (ExpiredPaymentDTO payment : expiredPayments) {
            try {
                Boolean closed = transactionTemplate.execute(status -> closeExpiredPaymentSafely(payment));
                if (Boolean.TRUE.equals(closed)) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            } catch (RuntimeException exception) {
                recordFailure(payment.getPaymentOrderId(), exception);
                result.setFailCount(result.getFailCount() + 1);
                if (result.getFailedPaymentOrderIds().size() < 3) {
                    result.getFailedPaymentOrderIds().add(payment.getPaymentOrderId());
                }
            }
        }
        return result;
    }

    private void cleanupStaleRetries() {
        try {
            paymentMapper.deleteStaleExpiryCloseRetries(batchSize);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to clean stale expiry close retry markers, exceptionType={}",
                    exception.getClass().getName());
        }
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new java.util.ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private boolean closeExpiredPayment(ExpiredPaymentDTO payment) {
        int closedRows = paymentMapper.markExpiredUnsubmittedPaymentClosed(payment.getPaymentOrderId());
        if (closedRows != 1) {
            paymentMapper.deleteExpiryCloseRetry(payment.getPaymentOrderId());
            return false;
        }
        if (paymentMapper.updatePrepayStatusByPaymentOrderId(
                payment.getPaymentOrderId(), "已失效", "danger") != 1) {
            throw new IllegalStateException("expired payment prepay status was not applied atomically");
        }
        String eventNo = "EVT" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String eventPayload = "{\"source\":\"scheduler\",\"reason\":\"PREPAY_EXPIRED\"}";
        if (paymentMapper.insertEvent(eventNo, "PAYMENT_EXPIRED_CLOSED", payment.getPaymentOrderId(),
                payment.getOrderNo(), eventPayload) != 1) {
            throw new IllegalStateException("expired payment event was not created atomically");
        }
        PaymentProcessLogEntity processLog = new PaymentProcessLogEntity();
        processLog.setLogNo(eventNo);
        processLog.setPaymentOrderId(payment.getPaymentOrderId());
        processLog.setOrderNo(payment.getOrderNo());
        processLog.setSourceType("PAYMENT_EVENT");
        processLog.setSourceNo(eventNo);
        processLog.setProcessStage("业务事件");
        processLog.setLogLevel("INFO");
        processLog.setLogLevelType("info");
        processLog.setMessage("事件类型=PAYMENT_EXPIRED_CLOSED");
        processLog.setSource("payment-core");
        if (paymentMapper.insertPaymentProcessLog(processLog) != 1) {
            throw new IllegalStateException("expired payment process log was not created atomically");
        }
        paymentMapper.deleteExpiryCloseRetry(payment.getPaymentOrderId());
        return true;
    }

    private boolean closeExpiredPaymentSafely(ExpiredPaymentDTO payment) {
        try {
            return closeExpiredPayment(payment);
        } catch (RuntimeException exception) {
            throw sanitizeException(exception);
        }
    }

    private void recordFailure(String paymentOrderId, RuntimeException exception) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    paymentMapper.recordExpiryCloseFailure(
                            paymentOrderId,
                            failureMessage(exception),
                            retryIntervalSeconds,
                            retryBackoffFactor,
                            maxRetryIntervalSeconds);
                } catch (RuntimeException markerException) {
                    throw sanitizeException(markerException);
                }
            });
        } catch (RuntimeException markerException) {
            LOGGER.warn("Failed to persist expiry close retry marker, paymentOrderId={}, exceptionType={}",
                    paymentOrderId, exceptionType(markerException));
        }
    }

    private String failureMessage(RuntimeException exception) {
        if (exception instanceof SanitizedExpiryCloseException) {
            return exception.getMessage();
        }
        return buildFailureMessage(exception);
    }

    private RuntimeException sanitizeException(RuntimeException exception) {
        return exception instanceof SanitizedExpiryCloseException
                ? exception
                : new SanitizedExpiryCloseException(exception);
    }

    private String exceptionType(RuntimeException exception) {
        return exception instanceof SanitizedExpiryCloseException
                ? ((SanitizedExpiryCloseException) exception).getOriginalExceptionType()
                : exception.getClass().getName();
    }

    private static String buildFailureMessage(RuntimeException exception) {
        String exceptionType = exception.getClass().getSimpleName();
        String message = FAILURE_CODE + ":"
                + (exceptionType.isEmpty() ? RuntimeException.class.getSimpleName() : exceptionType);
        return message.length() <= MAX_FAILURE_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private static final class SanitizedExpiryCloseException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final String originalExceptionType;

        private SanitizedExpiryCloseException(RuntimeException exception) {
            super(buildFailureMessage(exception), null, false, false);
            this.originalExceptionType = exception.getClass().getName();
        }

        private String getOriginalExceptionType() {
            return originalExceptionType;
        }
    }
}
