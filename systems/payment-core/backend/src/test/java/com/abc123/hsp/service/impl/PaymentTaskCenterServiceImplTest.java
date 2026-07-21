package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付任务中心服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentTaskCenterServiceImplTest {

    @Mock
    private PaymentTaskCenterMapper paymentTaskCenterMapper;
    @Mock
    private PaymentExpiryTaskService paymentExpiryTaskService;
    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private RefundMapper refundMapper;

    @Test
    void shouldRunCloseExpiredPayments() {
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(2);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runCloseExpiredPayments();

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRunRepublishFailedEvents() {
        when(paymentEventMapper.findFailedEventNos()).thenReturn(Arrays.asList("EVT-1", "EVT-2"));
        when(paymentEventMapper.markRepublished("EVT-1")).thenReturn(1);
        when(paymentEventMapper.markRepublished("EVT-2")).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runRepublishFailedEvents();

        verify(paymentEventMapper).findFailedEventNos();
        verify(paymentEventMapper).markRepublished("EVT-1");
        verify(paymentEventMapper).markRepublished("EVT-2");
    }

    @Test
    void shouldRunAutoRepublishFailedEvents() {
        when(paymentEventMapper.findFailedEventNos()).thenReturn(Collections.singletonList("EVT-AUTO-1"));
        when(paymentEventMapper.markRepublished("EVT-AUTO-1")).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runAutoRepublishFailedEvents();

        verify(paymentEventMapper).findFailedEventNos();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "payment-event-scheduler".equals(entity.getTriggeredBy())
        ));
    }

    @Test
    void shouldRunAutoCloseExpiredPayments() {
        when(paymentExpiryTaskService.closeExpiredPayments()).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runAutoCloseExpiredPayments();

        verify(paymentExpiryTaskService).closeExpiredPayments();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRunAutoRetryFailedRefunds() {
        when(refundMapper.findFailedRefundOrderIds()).thenReturn(Collections.singletonList("REF-AUTO-1"));
        when(refundMapper.updateRefundStatus("REF-AUTO-1", "FAIL", "PROCESSING", "warn", false)).thenReturn(1);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runAutoRetryFailedRefunds();

        verify(refundMapper).findFailedRefundOrderIds();
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "AUTO".equals(entity.getRunMode()) && "refund-retry-scheduler".equals(entity.getTriggeredBy())
        ));
    }

    @Test
    void shouldListTaskRunsWithDerivedFields() {
        PaymentTaskRunLogItemDTO item = new PaymentTaskRunLogItemDTO();
        item.setTaskCode("PAYMENT_EVENT_RETRY");
        item.setProcessedCount(2);
        item.setFailCount(1);
        when(paymentTaskCenterMapper.countTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        when(paymentTaskCenterMapper.findTaskRunLogs(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.Collections.singletonList(item));

        PaymentTaskRunLogQueryDTO query = new PaymentTaskRunLogQueryDTO();
        query.setPageNo(0);
        query.setPageSize(200);
        PageResultDTO<PaymentTaskRunLogItemDTO> result = new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).listTaskRuns(query);

        Assertions.assertEquals(1L, result.getTotal());
        Assertions.assertEquals(1, result.getPageNo());
        Assertions.assertEquals(100, result.getPageSize());
        Assertions.assertEquals("P1", result.getItems().get(0).getSeverityLevel());
        Assertions.assertEquals("/payment-events", result.getItems().get(0).getRecommendedRoute());
    }
}
