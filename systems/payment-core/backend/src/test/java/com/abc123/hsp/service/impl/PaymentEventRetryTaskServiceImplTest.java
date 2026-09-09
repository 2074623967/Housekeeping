package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentEventRetryResultDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
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
 * 支付事件批次重发服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventRetryTaskServiceImplTest {

    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private PaymentEventDispatchService paymentEventDispatchService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldIsolateUnexpectedFailureAndContinueRetryingEvents() {
        when(paymentEventMapper.findAllFailedEventNos())
                .thenReturn(Arrays.asList("EVT-SUCCESS", "EVT-THROW", "EVT-FAILED"));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(paymentEventDispatchService.republish(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String eventNo = invocation.getArgument(0);
                    if ("EVT-THROW".equals(eventNo)) {
                        throw new IllegalStateException("status update failed");
                    }
                    return "EVT-SUCCESS".equals(eventNo);
                });

        PaymentEventRetryResultDTO result = new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager)
                .retryFailedEvents(false);

        Assertions.assertEquals(3, result.getProcessedCount());
        Assertions.assertEquals(1, result.getSuccessCount());
        Assertions.assertEquals(2, result.getFailCount());
        Assertions.assertEquals(Arrays.asList("EVT-THROW", "EVT-FAILED"), result.getFailedEventNos());
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager).rollback(transactionStatus);
        verify(paymentEventDispatchService).republish("EVT-FAILED");
    }

    @Test
    void shouldUseDueEventsForAutomaticRetry() {
        when(paymentEventMapper.findDueFailedEventNos()).thenReturn(Collections.emptyList());

        PaymentEventRetryResultDTO result = new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager)
                .retryFailedEvents(true);

        Assertions.assertEquals(0, result.getProcessedCount());
        verify(paymentEventMapper).findDueFailedEventNos();
        verify(paymentEventMapper, never()).findAllFailedEventNos();
        verify(transactionManager, never()).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
    }
}
