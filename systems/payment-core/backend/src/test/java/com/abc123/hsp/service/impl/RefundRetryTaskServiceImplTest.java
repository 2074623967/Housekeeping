package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.dto.RefundRetryResultDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 失败退款批次重试服务测试。
 */
@ExtendWith(MockitoExtension.class)
class RefundRetryTaskServiceImplTest {

    @Mock
    private RefundMapper refundMapper;
    @Mock
    private RefundService refundService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldContinueRetryingRefundsAfterOneTransactionFails() {
        String sensitiveMessage = "jdbc:mysql://internal/payment password=refund-secret\ninvalid SQL";
        when(refundMapper.findFailedRefundOrderIds(100))
                .thenReturn(Arrays.asList("REF-FIRST", "REF-FAIL", "REF-LAST"));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("REF-FAIL".equals(invocation.getArgument(0))) {
                throw new IllegalStateException(sensitiveMessage);
            }
            return null;
        }).when(refundService).retryForTask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("refund-retry-scheduler"));

        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        transactionAppender.start();
        transactionLogger.addAppender(transactionAppender);
        try {
            RefundRetryResultDTO result = new RefundRetryTaskServiceImpl(
                    refundMapper, refundService, transactionManager)
                    .retryFailedRefunds("refund-retry-scheduler");

            Assertions.assertEquals(3, result.getProcessedCount());
            Assertions.assertEquals(2, result.getSuccessCount());
            Assertions.assertEquals(1, result.getFailCount());
            Assertions.assertEquals(Collections.singletonList("REF-FAIL"), result.getFailedRefundOrderIds());
            verify(transactionManager, times(2)).commit(transactionStatus);
            verify(transactionManager).rollback(transactionStatus);
            verify(refundService).retryForTask("REF-LAST", "refund-retry-scheduler");
            Assertions.assertTrue(transactionAppender.list.stream().anyMatch(event ->
                    event.getThrowableProxy() != null
                            && "REFUND_RETRY_TRANSACTION_FAILED:IllegalStateException"
                            .equals(event.getThrowableProxy().getMessage())
                            && event.getThrowableProxy().getCause() == null
                            && event.getThrowableProxy().getStackTraceElementProxyArray().length == 0));
            Assertions.assertTrue(transactionAppender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("refund-secret")
                            || (event.getThrowableProxy() != null
                            && String.valueOf(event.getThrowableProxy().getMessage()).contains("refund-secret"))));
        } finally {
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }
    }

    @Test
    void shouldClampConfiguredBatchSizeBeforeReadingCandidates() {
        when(refundMapper.findFailedRefundOrderIds(1000)).thenReturn(Collections.emptyList());
        when(refundMapper.findFailedRefundOrderIds(1)).thenReturn(Collections.emptyList());

        new RefundRetryTaskServiceImpl(refundMapper, refundService, transactionManager, 1001)
                .retryFailedRefunds("refund-retry-scheduler");
        new RefundRetryTaskServiceImpl(refundMapper, refundService, transactionManager, 0)
                .retryFailedRefunds("payment-core-admin");

        verify(refundMapper).findFailedRefundOrderIds(1000);
        verify(refundMapper).findFailedRefundOrderIds(1);
    }

    @Test
    void shouldTreatNullRefundRetryCandidatesAsEmpty() {
        when(refundMapper.findFailedRefundOrderIds(100)).thenReturn(null);

        RefundRetryResultDTO result = new RefundRetryTaskServiceImpl(
                refundMapper, refundService, transactionManager).retryFailedRefunds("refund-retry-scheduler");

        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
    }
}
