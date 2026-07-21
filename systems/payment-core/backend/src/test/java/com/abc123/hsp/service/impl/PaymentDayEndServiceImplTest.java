package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.mapper.PaymentDayEndMapper;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付日终处理服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentDayEndServiceImplTest {

    @Mock
    private PaymentDayEndMapper paymentDayEndMapper;

    @Test
    void shouldLoadOverview() {
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setTotalBatchCount(1);
        overview.setCompletedBatchCount(1);
        overview.setAbnormalBatchCount(0);
        overview.setLatestBatchStatus("COMPLETED");
        overview.setLatestChannelSuccessAmount("¥268.00");
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        verify(paymentDayEndMapper).findOverviewSummary();
        verify(paymentDayEndMapper).findRecentBatches();
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", result.getLatestBatchStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getAlerts());
    }

    @Test
    void shouldRunDayEndBatch() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countPaymentsByDate(bizDate)).thenReturn(2);
        when(paymentDayEndMapper.countSuccessPaymentsByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumSuccessPaymentAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countChannelSuccessByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumChannelSuccessAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countInternalSuccessByDate(bizDate)).thenReturn(0);
        when(paymentDayEndMapper.sumInternalSuccessAmountByDate(bizDate)).thenReturn(BigDecimal.ZERO);
        when(paymentDayEndMapper.countPaymentSuccessGapByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumPaymentSuccessGapAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countSuccessRefundsByDate(bizDate)).thenReturn(0);
        when(paymentDayEndMapper.sumSuccessRefundAmountByDate(bizDate)).thenReturn(BigDecimal.ZERO);
        when(paymentDayEndMapper.countChannelAbnormalByDate(bizDate)).thenReturn(0);
        when(paymentDayEndMapper.countInternalAbnormalByDate(bizDate)).thenReturn(0);
        when(paymentDayEndMapper.countPendingRefundByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumPendingRefundAmountByDate(bizDate)).thenReturn(new BigDecimal("68.00"));
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(new PaymentDayEndOverviewDTO());
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());

        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setRunMode("AUTO");
        request.setTriggeredBy("system");

        new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), captor.getValue().getPaymentTotalCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), captor.getValue().getPaymentSuccessGapCount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("68.00"), captor.getValue().getPendingRefundAmount());
    }
}
