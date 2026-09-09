package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(refundMapper.findFailedRefundOrderIds())
                .thenReturn(Arrays.asList("REF-FIRST", "REF-FAIL", "REF-LAST"));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        org.mockito.Mockito.doAnswer(invocation -> {
            if ("REF-FAIL".equals(invocation.getArgument(0))) {
                throw new IllegalStateException("channel submit failed");
            }
            return null;
        }).when(refundService).retryForTask(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("refund-retry-scheduler"));

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
    }
}
