package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.springframework.transaction.support.TransactionTemplate;

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
        String sensitiveMessage = "jdbc:mysql://internal/payment password=event-secret\ninvalid SQL";
        when(paymentEventMapper.findAllFailedEventNos(100))
                .thenReturn(Arrays.asList("EVT-SUCCESS", "EVT-THROW", "EVT-FAILED"));
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(paymentEventDispatchService.republish(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("finance-ops")))
                .thenAnswer(invocation -> {
                    String eventNo = invocation.getArgument(0);
                    if ("EVT-THROW".equals(eventNo)) {
                        throw new IllegalStateException(sensitiveMessage);
                    }
                    return "EVT-SUCCESS".equals(eventNo);
                });
        when(paymentEventMapper.updateLastRepublishAudit("EVT-SUCCESS", "finance-ops")).thenReturn(1);

        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        transactionAppender.start();
        transactionLogger.addAppender(transactionAppender);
        try {
            PaymentEventRetryResultDTO result = new PaymentEventRetryTaskServiceImpl(
                    paymentEventMapper, paymentEventDispatchService, transactionManager)
                    .retryFailedEvents(false, " finance-ops ");

            Assertions.assertEquals(3, result.getProcessedCount());
            Assertions.assertEquals(1, result.getSuccessCount());
            Assertions.assertEquals(2, result.getFailCount());
            Assertions.assertEquals(Arrays.asList("EVT-THROW", "EVT-FAILED"), result.getFailedEventNos());
            verify(transactionManager, times(2)).commit(transactionStatus);
            verify(transactionManager).rollback(transactionStatus);
            verify(paymentEventDispatchService).republish("EVT-FAILED", "finance-ops");
            verify(paymentEventMapper).updateLastRepublishAudit("EVT-SUCCESS", "finance-ops");
            Assertions.assertTrue(transactionAppender.list.stream().anyMatch(event ->
                    event.getThrowableProxy() != null
                            && "PAYMENT_EVENT_RETRY_TRANSACTION_FAILED:IllegalStateException"
                            .equals(event.getThrowableProxy().getMessage())
                            && event.getThrowableProxy().getCause() == null
                            && event.getThrowableProxy().getStackTraceElementProxyArray().length == 0));
            Assertions.assertTrue(transactionAppender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("event-secret")
                            || (event.getThrowableProxy() != null
                            && String.valueOf(event.getThrowableProxy().getMessage()).contains("event-secret"))));
        } finally {
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }
    }

    @Test
    void shouldUseDueEventsForAutomaticRetry() {
        when(paymentEventMapper.findDueFailedEventNos(100)).thenReturn(Collections.emptyList());

        PaymentEventRetryResultDTO result = new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager)
                .retryFailedEvents(true, "payment-event-scheduler");

        Assertions.assertEquals(0, result.getProcessedCount());
        verify(paymentEventMapper).findDueFailedEventNos(100);
        verify(paymentEventMapper, never()).findAllFailedEventNos(org.mockito.ArgumentMatchers.anyInt());
        verify(transactionManager, never()).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
    }

    @Test
    void shouldTreatNullEventCandidatesAsEmpty() {
        when(paymentEventMapper.findDueFailedEventNos(100)).thenReturn(null);

        PaymentEventRetryResultDTO result = new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager)
                .retryFailedEvents(true, "payment-event-scheduler");

        Assertions.assertEquals(0, result.getProcessedCount());
        Assertions.assertEquals(0, result.getSuccessCount());
        Assertions.assertEquals(0, result.getFailCount());
        verify(transactionManager, never()).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
    }

    @Test
    void shouldRejectInvalidOperatorBeforeReadingCandidates() {
        PaymentEventRetryTaskServiceImpl service = new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.retryFailedEvents(false, "   "));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.retryFailedEvents(true, String.join("", Collections.nCopies(65, "A"))));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.retryFailedEvents(false, "finance-ops\u0085"));

        org.mockito.Mockito.verifyNoInteractions(
                paymentEventMapper, paymentEventDispatchService, transactionManager);
    }

    @Test
    void shouldClampConfiguredBatchSizeBeforeReadingCandidates() {
        when(paymentEventMapper.findDueFailedEventNos(1000)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.findAllFailedEventNos(1)).thenReturn(Collections.emptyList());

        new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager, 1001)
                .retryFailedEvents(true, "payment-event-scheduler");
        new PaymentEventRetryTaskServiceImpl(
                paymentEventMapper, paymentEventDispatchService, transactionManager, 0)
                .retryFailedEvents(false, "finance-ops");

        verify(paymentEventMapper).findDueFailedEventNos(1000);
        verify(paymentEventMapper).findAllFailedEventNos(1);
    }
}
