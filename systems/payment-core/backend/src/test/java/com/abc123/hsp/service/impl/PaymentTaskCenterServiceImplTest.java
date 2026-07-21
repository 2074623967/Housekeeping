package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
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
import org.mockito.ArgumentCaptor;
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
        verify(paymentTaskCenterMapper).insertTaskRunLog(org.mockito.ArgumentMatchers.argThat(
                entity -> "P2".equals(entity.getSeverityLevel())
                        && "纳入当班跟进".equals(entity.getEscalationStatus())
                        && entity.getSuggestedAction().contains("复核下游是否完成收口")
        ));
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
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.any());
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
        Assertions.assertEquals("P2", result.getItems().get(0).getSeverityLevel());
        Assertions.assertEquals("纳入当班跟进", result.getItems().get(0).getEscalationStatus());
        Assertions.assertEquals("/payment-events", result.getItems().get(0).getRecommendedRoute());
    }

    @Test
    void shouldEscalateImmediatelyWhenRefundRetryKeepsFailing() {
        when(refundMapper.findFailedRefundOrderIds()).thenReturn(Arrays.asList("REF-1", "REF-2"));
        when(refundMapper.updateRefundStatus("REF-1", "FAIL", "PROCESSING", "warn", false)).thenReturn(0);
        when(refundMapper.updateRefundStatus("REF-2", "FAIL", "PROCESSING", "warn", false)).thenReturn(0);
        when(paymentTaskCenterMapper.findOverviewSummary()).thenReturn(new PaymentTaskCenterOverviewDTO());
        when(paymentTaskCenterMapper.findRecentTaskRuns()).thenReturn(Collections.emptyList());

        new PaymentTaskCenterServiceImpl(
                paymentTaskCenterMapper,
                paymentExpiryTaskService,
                paymentEventMapper,
                refundMapper
        ).runRetryFailedRefunds();

        ArgumentCaptor<PaymentTaskRunLogEntity> logCaptor = ArgumentCaptor.forClass(PaymentTaskRunLogEntity.class);
        verify(paymentTaskCenterMapper).insertTaskRunLog(logCaptor.capture());
        Assertions.assertEquals("P1", logCaptor.getValue().getSeverityLevel());
        Assertions.assertEquals("升级值班负责人", logCaptor.getValue().getEscalationStatus());
        Assertions.assertTrue(logCaptor.getValue().getSuggestedAction().contains("优先核对退款渠道响应"));
    }
}
