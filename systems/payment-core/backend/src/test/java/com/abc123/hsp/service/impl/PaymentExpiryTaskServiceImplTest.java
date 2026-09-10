package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * 支付超时关单任务服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentExpiryTaskServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldCloseExpiredPaymentAndWriteEvent() {
        ExpiredPaymentDTO payment = new ExpiredPaymentDTO();
        payment.setPaymentOrderId("PAY-EXPIRED");
        payment.setOrderNo("ORD-EXPIRED");
        payment.setChannelTransactionNo("CHANNEL-EXPIRED");
        when(paymentMapper.findExpiredPayments(100)).thenReturn(Collections.singletonList(payment));
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-EXPIRED")).thenReturn(1);
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentExpiryCloseResultDTO result =
                service(100, 60, 2, 3600).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getSkippedCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getFailCount());
        org.junit.jupiter.api.Assertions.assertTrue(result.getFailedPaymentOrderIds().isEmpty());
        verify(transactionManager).commit(transactionStatus);
        verify(paymentMapper).markExpiredUnsubmittedPaymentClosed("PAY-EXPIRED");
        verify(paymentMapper).updatePaymentAttemptStatusByPaymentOrderId(
                "PAY-EXPIRED", "已关闭", "danger");
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-EXPIRED", "已失效", "danger");
        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_EXPIRED_CLOSED"),
                org.mockito.ArgumentMatchers.eq("PAY-EXPIRED"),
                org.mockito.ArgumentMatchers.eq("ORD-EXPIRED"),
                org.mockito.ArgumentMatchers.contains("PREPAY_EXPIRED"));
        verify(paymentMapper).insertPaymentProcessLog(org.mockito.ArgumentMatchers.argThat(log ->
                "PAYMENT_EVENT".equals(log.getSourceType())
                        && "PAY-EXPIRED".equals(log.getPaymentOrderId())
                        && "ORD-EXPIRED".equals(log.getOrderNo())
                        && log.getMessage().contains("PAYMENT_EXPIRED_CLOSED")));
    }

    @Test
    void shouldContinueClosingPaymentsAfterOneTransactionFails() {
        ExpiredPaymentDTO first = buildPayment("PAY-FIRST", "ORD-FIRST");
        ExpiredPaymentDTO failed = buildPayment("PAY-FAIL", "ORD-FAIL");
        ExpiredPaymentDTO last = buildPayment("PAY-LAST", "ORD-LAST");
        when(paymentMapper.findExpiredPayments(100)).thenReturn(Arrays.asList(first, failed, last));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("PAY-FAIL".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("database write failed");
            }
            return 1;
        }).when(paymentMapper).markExpiredUnsubmittedPaymentClosed(
                org.mockito.ArgumentMatchers.anyString());

        PaymentExpiryCloseResultDTO result =
                service(100, 60, 2, 3600).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(3, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getSkippedCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getFailCount());
        org.junit.jupiter.api.Assertions.assertEquals(Collections.singletonList("PAY-FAIL"),
                result.getFailedPaymentOrderIds());
        verify(transactionManager, times(3)).commit(transactionStatus);
        verify(transactionManager).rollback(transactionStatus);
        verify(paymentMapper).markExpiredUnsubmittedPaymentClosed("PAY-LAST");
        verify(paymentMapper).recordExpiryCloseFailure(
                org.mockito.ArgumentMatchers.eq("PAY-FAIL"),
                org.mockito.ArgumentMatchers.contains("database write failed"),
                org.mockito.ArgumentMatchers.eq(60),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(3600));

        ArgumentCaptor<String> eventNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper, times(2)).insertEvent(
                eventNoCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_EXPIRED_CLOSED"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("PREPAY_EXPIRED"));
        List<String> eventNos = eventNoCaptor.getAllValues();
        Set<String> distinctEventNos = new HashSet<String>(eventNos);
        org.junit.jupiter.api.Assertions.assertEquals(2, distinctEventNos.size());
    }

    @Test
    void shouldSkipSideEffectsWhenPaymentBecameIneligibleAfterCandidateQuery() {
        ExpiredPaymentDTO payment = buildPayment("PAY-RACING", "ORD-RACING");
        when(paymentMapper.findExpiredPayments(100)).thenReturn(Collections.singletonList(payment));
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-RACING")).thenReturn(0);
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentExpiryCloseResultDTO result =
                service(100, 60, 2, 3600).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getSuccessCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getSkippedCount());
        org.junit.jupiter.api.Assertions.assertEquals(0, result.getFailCount());
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper).deleteExpiryCloseRetry("PAY-RACING");
    }

    @Test
    void shouldClampConfiguredBatchSizeToSafeBounds() {
        when(paymentMapper.findExpiredPayments(1)).thenReturn(Collections.emptyList());
        when(paymentMapper.findExpiredPayments(1000)).thenReturn(Collections.emptyList());

        service(0, 60, 2, 3600).closeExpiredPayments();
        service(1001, 60, 2, 3600).closeExpiredPayments();

        verify(paymentMapper).findExpiredPayments(1);
        verify(paymentMapper).findExpiredPayments(1000);
    }

    @Test
    void shouldClampRetryPolicyAndContinueWhenFailureMarkerCannotBeSaved() {
        ExpiredPaymentDTO payment = buildPayment("PAY-FAIL", "ORD-FAIL");
        when(paymentMapper.findExpiredPayments(100)).thenReturn(Collections.singletonList(payment));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-FAIL"))
                .thenThrow(new IllegalStateException("database write failed"));
        when(paymentMapper.recordExpiryCloseFailure(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("retry marker failed"));

        PaymentExpiryCloseResultDTO result = service(100, 0, 0, 0).closeExpiredPayments();

        org.junit.jupiter.api.Assertions.assertEquals(1, result.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getFailCount());
        org.junit.jupiter.api.Assertions.assertEquals(
                Collections.singletonList("PAY-FAIL"), result.getFailedPaymentOrderIds());
        verify(paymentMapper).recordExpiryCloseFailure(
                org.mockito.ArgumentMatchers.eq("PAY-FAIL"),
                org.mockito.ArgumentMatchers.contains("database write failed"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(1));
        verify(transactionManager, times(2)).rollback(transactionStatus);
    }

    @Test
    void shouldBoundPersistedFailureMessage() {
        ExpiredPaymentDTO payment = buildPayment("PAY-LONG-ERROR", "ORD-LONG-ERROR");
        StringBuilder longMessage = new StringBuilder();
        for (int index = 0; index < 600; index++) {
            longMessage.append('x');
        }
        when(paymentMapper.findExpiredPayments(100)).thenReturn(Collections.singletonList(payment));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(paymentMapper.markExpiredUnsubmittedPaymentClosed("PAY-LONG-ERROR"))
                .thenThrow(new IllegalStateException(longMessage.toString()));

        service(100, 60, 2, 3600).closeExpiredPayments();

        ArgumentCaptor<String> failureMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).recordExpiryCloseFailure(
                org.mockito.ArgumentMatchers.eq("PAY-LONG-ERROR"),
                failureMessageCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(60),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(3600));
        org.junit.jupiter.api.Assertions.assertEquals(512, failureMessageCaptor.getValue().length());
        org.junit.jupiter.api.Assertions.assertTrue(
                failureMessageCaptor.getValue().startsWith("IllegalStateException: "));
    }

    private PaymentExpiryTaskServiceImpl service(int batchSize,
                                                  int retryIntervalSeconds,
                                                  int retryBackoffFactor,
                                                  int maxRetryIntervalSeconds) {
        return new PaymentExpiryTaskServiceImpl(
                paymentMapper,
                transactionManager,
                batchSize,
                retryIntervalSeconds,
                retryBackoffFactor,
                maxRetryIntervalSeconds);
    }

    private ExpiredPaymentDTO buildPayment(String paymentOrderId, String orderNo) {
        ExpiredPaymentDTO payment = new ExpiredPaymentDTO();
        payment.setPaymentOrderId(paymentOrderId);
        payment.setOrderNo(orderNo);
        payment.setChannelTransactionNo("CHANNEL-" + paymentOrderId);
        return payment;
    }
}
