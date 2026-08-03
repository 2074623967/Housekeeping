package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.mapper.PaymentDayEndMapper;
import java.math.BigDecimal;
import java.util.Arrays;
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
        overview.setLatestBizDate("2026-07-20");
        overview.setLatestBatchStatus("COMPLETED");
        overview.setLatestPaymentSuccessGapCount(0);
        overview.setOpenPendingRefundCount(1);
        overview.setLatestChannelSuccessAmount("¥268.00");
        PaymentDayEndBatchListItemDTO batch = new PaymentDayEndBatchListItemDTO();
        batch.setBatchNo("DEB001");
        batch.setPendingRefundCount(1);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Arrays.asList(batch));

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        verify(paymentDayEndMapper).findOverviewSummary();
        verify(paymentDayEndMapper).findRecentBatches();
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", result.getLatestBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals("有条件进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals("CONDITIONAL", result.getRecentBatches().get(0).getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getAlerts());
        org.junit.jupiter.api.Assertions.assertEquals("/payment-events?publishStatus=FAILED_OR_DEAD_LETTER", result.getAlerts().get(1).getActionRoute());
    }

    @Test
    void shouldExportPaymentDayEndBatchesCsv() {
        PaymentDayEndBatchListItemDTO batch = new PaymentDayEndBatchListItemDTO();
        batch.setBatchNo("DEB001");
        batch.setBizDate("2026-08-02");
        batch.setRunMode("MANUAL");
        batch.setBatchStatus("WARNING");
        batch.setPaymentTotalCount(2);
        batch.setPaymentSuccessCount(1);
        batch.setPaymentSuccessAmount("¥268.00");
        batch.setChannelSuccessCount(1);
        batch.setChannelSuccessAmount("¥268.00");
        batch.setInternalSuccessCount(1);
        batch.setInternalSuccessAmount("¥268.00");
        batch.setPaymentSuccessGapCount(0);
        batch.setPaymentSuccessGapAmount("¥0.00");
        batch.setRefundSuccessCount(0);
        batch.setRefundSuccessAmount("¥0.00");
        batch.setChannelAbnormalCount(0);
        batch.setInternalAbnormalCount(0);
        batch.setPendingRefundCount(1);
        batch.setPendingRefundAmount("¥68.00");
        batch.setSummaryComment("待退款收口");
        batch.setTriggeredBy("finance-ops");
        batch.setCreatedAt("2026-08-03 09:00:00");
        batch.setCompletedAt("2026-08-03 09:01:00");
        when(paymentDayEndMapper.findAllBatchesForExport()).thenReturn(Collections.singletonList(batch));

        String csv = new PaymentDayEndServiceImpl(paymentDayEndMapper).exportBatchesCsv();

        verify(paymentDayEndMapper).findAllBatchesForExport();
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("批次号,业务日期"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"DEB001\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("CONDITIONAL"));
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

    @Test
    void shouldBlockReconciliationWhenPrimaryFactsAreNotClosed() {
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate("2026-07-20");
        overview.setLatestBatchStatus("WARNING");
        overview.setOpenChannelAbnormalCount(2);
        overview.setOpenInternalAbnormalCount(1);
        overview.setLatestPaymentSuccessGapCount(3);
        overview.setOpenPendingRefundCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals("danger", result.getReconciliationReadinessType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getReconciliationReadinessSummary().contains("支付成功差异 3 笔"));
    }
}
