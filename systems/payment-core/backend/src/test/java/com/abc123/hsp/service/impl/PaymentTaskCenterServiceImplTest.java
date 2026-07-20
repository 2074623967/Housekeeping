package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.Arrays;
import java.util.Collections;
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
}
