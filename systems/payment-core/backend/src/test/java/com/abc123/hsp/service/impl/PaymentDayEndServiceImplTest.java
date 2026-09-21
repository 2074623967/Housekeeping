package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.dto.PaymentDayEndRunRequestDTO;
import com.abc123.hsp.mapper.PaymentDayEndMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.PaymentTaskLeaseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
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
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenPendingRefundCount(1);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        overview.setLatestChannelSuccessAmount("¥268.00");
        PaymentDayEndBatchListItemDTO batch = new PaymentDayEndBatchListItemDTO();
        batch.setBatchNo("DEB001");
        batch.setPendingPaymentCount(0);
        batch.setMissingPaymentSuccessTimeCount(0);
        batch.setPaymentSuccessFactMismatchCount(0);
        batch.setPendingRefundCount(1);
        batch.setRefundFailCount(0);
        batch.setMissingRefundSuccessTimeCount(0);
        batch.setRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Arrays.asList(batch));

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        verify(paymentDayEndMapper).findOverviewSummary();
        verify(paymentDayEndMapper).findRecentBatches();
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", result.getLatestBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals("有条件进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals("CONDITIONAL", result.getRecentBatches().get(0).getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getAlerts());
        org.junit.jupiter.api.Assertions.assertEquals(10, result.getAlerts().size());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_PAYMENT", result.getAlerts().get(0).getAlertType());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/payments?status=DAY_END_PENDING", result.getAlerts().get(0).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAYMENT_SUCCESS_TIME_MISSING", result.getAlerts().get(1).getAlertType());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/payments?status=SUCCESS_TIME_MISSING", result.getAlerts().get(1).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals("PAYMENT_SUCCESS_FACT_MISMATCH", result.getAlerts().get(2).getAlertType());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/payments?status=SUCCESS_FACT_MISMATCH", result.getAlerts().get(2).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals("PAYMENT_SUCCESS_GAP", result.getAlerts().get(3).getAlertType());
        org.junit.jupiter.api.Assertions.assertEquals("/payment-issues", result.getAlerts().get(3).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals("/payment-events?publishStatus=FAILED_OR_DEAD_LETTER", result.getAlerts().get(5).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/refunds?refundStatus=SUCCESS_TIME_MISSING", result.getAlerts().get(6).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/refunds?refundStatus=TERMINAL_FACT_MISMATCH", result.getAlerts().get(7).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/refunds?refundStatus=FAIL", result.getAlerts().get(8).getActionRoute());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/refunds?refundStatus=PENDING", result.getAlerts().get(9).getActionRoute());
    }

    @Test
    void shouldReturnEmptyRecentBatchesWhenMapperReturnsNull() {
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(null);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(null);

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        org.junit.jupiter.api.Assertions.assertNotNull(result.getAlerts());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getRecentBatches());
        org.junit.jupiter.api.Assertions.assertTrue(result.getRecentBatches().isEmpty());
    }

    @Test
    void shouldReturnBatchDetailAndReadiness() {
        PaymentDayEndBatchListItemDTO batch = new PaymentDayEndBatchListItemDTO();
        batch.setBatchNo("DAY-END-001");
        batch.setPendingPaymentCount(0);
        batch.setMissingPaymentSuccessTimeCount(0);
        batch.setPaymentSuccessFactMismatchCount(0);
        batch.setRefundFailCount(0);
        batch.setMissingRefundSuccessTimeCount(0);
        batch.setRefundTerminalFactMismatchCount(0);
        batch.setPaymentSuccessGapCount(0);
        batch.setPendingRefundCount(0);
        batch.setChannelAbnormalCount(0);
        batch.setInternalAbnormalCount(0);
        when(paymentDayEndMapper.findBatchByNo("DAY-END-001")).thenReturn(batch);

        PaymentDayEndBatchListItemDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper)
                .detail(" DAY-END-001 ");

        org.junit.jupiter.api.Assertions.assertEquals("READY", result.getReconciliationReadinessStatus());
        verify(paymentDayEndMapper).findBatchByNo("DAY-END-001");
    }

    @Test
    void shouldUseStableErrorCodeWhenBatchMissing() {
        when(paymentDayEndMapper.findBatchByNo("DAY-END-404")).thenReturn(null);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> new PaymentDayEndServiceImpl(paymentDayEndMapper).detail("DAY-END-404"));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_DAY_END_BATCH_NOT_FOUND, exception.getCode());
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
        batch.setPendingPaymentCount(2);
        batch.setPendingPaymentAmount("¥98.00");
        batch.setMissingPaymentSuccessTimeCount(0);
        batch.setPaymentSuccessFactMismatchCount(0);
        batch.setRefundSuccessCount(0);
        batch.setRefundSuccessAmount("¥0.00");
        batch.setRefundFailCount(0);
        batch.setRefundFailAmount("¥0.00");
        batch.setMissingRefundSuccessTimeCount(0);
        batch.setRefundTerminalFactMismatchCount(0);
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
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("待收口支付,待收口支付金额"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("退款终态事实不一致"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("退款失败单量,退款失败金额"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("支付成功事实不一致"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("BLOCKED"));
    }

    @Test
    void shouldRunDayEndBatch() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countBatchesByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.countPaymentsByDate(bizDate)).thenReturn(2);
        when(paymentDayEndMapper.countSuccessPaymentsByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumSuccessPaymentAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countChannelSuccessByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumChannelSuccessAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countInternalSuccessByDate(bizDate)).thenReturn(0);
        when(paymentDayEndMapper.sumInternalSuccessAmountByDate(bizDate)).thenReturn(BigDecimal.ZERO);
        when(paymentDayEndMapper.countPaymentSuccessGapByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumPaymentSuccessGapAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.countPendingPaymentsByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumPendingPaymentAmountByDate(bizDate)).thenReturn(new BigDecimal("88.00"));
        when(paymentDayEndMapper.countMissingPaymentSuccessTimeByDate(bizDate)).thenReturn(0);
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
        request.setRunMode(" rerun ");
        request.setTriggeredBy(" finance-ops ");

        new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), captor.getValue().getPaymentTotalCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), captor.getValue().getPaymentSuccessGapCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), captor.getValue().getPendingPaymentCount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("88.00"), captor.getValue().getPendingPaymentAmount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("68.00"), captor.getValue().getPendingRefundAmount());
        org.junit.jupiter.api.Assertions.assertEquals("RERUN", captor.getValue().getRunMode());
        org.junit.jupiter.api.Assertions.assertEquals("finance-ops", captor.getValue().getTriggeredBy());
    }

    @Test
    void shouldRequireManualForFirstRunAndRerunForExistingBatch() {
        String firstBizDate = "2026-07-17";
        String existingBizDate = "2026-07-18";
        when(paymentDayEndMapper.countBatchesByDate(firstBizDate)).thenReturn(0);
        when(paymentDayEndMapper.countBatchesByDate(existingBizDate)).thenReturn(2);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(paymentDayEndMapper);
        PaymentDayEndRunRequestDTO firstRerun = new PaymentDayEndRunRequestDTO();
        firstRerun.setBizDate(firstBizDate);
        firstRerun.setRunMode("RERUN");
        firstRerun.setTriggeredBy("finance-ops");
        PaymentDayEndRunRequestDTO duplicateManual = new PaymentDayEndRunRequestDTO();
        duplicateManual.setBizDate(existingBizDate);
        duplicateManual.setRunMode("MANUAL");
        duplicateManual.setTriggeredBy("finance-ops");

        IllegalArgumentException firstRerunError = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(firstRerun));
        IllegalArgumentException duplicateManualError = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(duplicateManual));

        org.junit.jupiter.api.Assertions.assertEquals(
                "该业务日尚无日终批次，首次执行请使用MANUAL", firstRerunError.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(
                "该业务日已有日终批次，请使用RERUN重检", duplicateManualError.getMessage());
        verify(paymentDayEndMapper, org.mockito.Mockito.never()).insertBatch(org.mockito.ArgumentMatchers.any());
        verify(paymentDayEndMapper, org.mockito.Mockito.never()).countPaymentsByDate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectConcurrentRunBeforeReadingDayEndFacts() {
        PaymentTaskLeaseService leaseService = org.mockito.Mockito.mock(PaymentTaskLeaseService.class);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, (org.springframework.transaction.support.TransactionTemplate) null);
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate("2026-07-19");
        request.setTriggeredBy("finance-ops");
        when(leaseService.tryAcquire(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:2026-07-19"),
                org.mockito.ArgumentMatchers.startsWith("finance-ops:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(null);

        IllegalArgumentException error = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(request));

        org.junit.jupiter.api.Assertions.assertEquals("该业务日正在执行日终处理，请稍后重试", error.getMessage());
        org.mockito.Mockito.verifyNoInteractions(paymentDayEndMapper);
    }

    @Test
    void shouldCommitBatchBeforeReleasingBusinessDateLease() {
        PaymentTaskCenterMapper leaseMapper = org.mockito.Mockito.mock(PaymentTaskCenterMapper.class);
        PaymentTaskLeaseService leaseService = new PaymentTaskLeaseService(leaseMapper);
        org.springframework.transaction.PlatformTransactionManager transactionManager =
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus transactionStatus =
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
        when(leaseMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:2026-07-19"),
                org.mockito.ArgumentMatchers.startsWith("finance-ops:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(1);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
        when(paymentDayEndMapper.countBatchesByDate("2026-07-19")).thenReturn(0);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, transactionManager);
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate("2026-07-19");
        request.setTriggeredBy("finance-ops");

        service.run(request);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                paymentDayEndMapper, transactionManager, leaseMapper);
        inOrder.verify(paymentDayEndMapper).insertBatch(org.mockito.ArgumentMatchers.any());
        inOrder.verify(transactionManager).commit(transactionStatus);
        inOrder.verify(leaseMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:2026-07-19"),
                org.mockito.ArgumentMatchers.startsWith("finance-ops:"));
    }

    @Test
    void shouldRollbackFailedBatchBeforeReleasingBusinessDateLease() {
        PaymentTaskCenterMapper leaseMapper = org.mockito.Mockito.mock(PaymentTaskCenterMapper.class);
        PaymentTaskLeaseService leaseService = new PaymentTaskLeaseService(leaseMapper);
        org.springframework.transaction.PlatformTransactionManager transactionManager =
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus transactionStatus =
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
        when(leaseMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:2026-07-19"),
                org.mockito.ArgumentMatchers.startsWith("finance-ops:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(1);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
        when(paymentDayEndMapper.countBatchesByDate("2026-07-19")).thenReturn(0);
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed"))
                .when(paymentDayEndMapper).insertBatch(org.mockito.ArgumentMatchers.any());
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, transactionManager);
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate("2026-07-19");
        request.setTriggeredBy("finance-ops");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.run(request));

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                paymentDayEndMapper, transactionManager, leaseMapper);
        inOrder.verify(paymentDayEndMapper).insertBatch(org.mockito.ArgumentMatchers.any());
        inOrder.verify(transactionManager).rollback(transactionStatus);
        inOrder.verify(leaseMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:2026-07-19"),
                org.mockito.ArgumentMatchers.startsWith("finance-ops:"));
    }

    @Test
    void shouldRunPreviousShanghaiBusinessDayAutomatically() {
        LocalDate expectedBizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        when(paymentDayEndMapper.countBatchesByDate(expectedBizDate.toString())).thenReturn(0);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(paymentDayEndMapper);

        com.abc123.hsp.dto.PaymentDayEndAutoRunResultDTO result = service.runAutomatically();

        org.junit.jupiter.api.Assertions.assertTrue(result.getExecuted());
        org.junit.jupiter.api.Assertions.assertNull(result.getSkipReason());
        org.junit.jupiter.api.Assertions.assertEquals(expectedBizDate.toString(), result.getBizDate());
        org.junit.jupiter.api.Assertions.assertEquals("COMPLETED", result.getBatchStatus());
        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(captor.getValue().getBatchNo(), result.getBatchNo());
        org.junit.jupiter.api.Assertions.assertEquals(expectedBizDate.toString(), captor.getValue().getBizDate());
        org.junit.jupiter.api.Assertions.assertEquals("AUTO", captor.getValue().getRunMode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "payment-day-end-scheduler", captor.getValue().getTriggeredBy());
    }

    @Test
    void shouldSkipAutomaticDayEndWhenBusinessDateAlreadyHasBatch() {
        LocalDate expectedBizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        when(paymentDayEndMapper.countBatchesByDate(expectedBizDate.toString())).thenReturn(1);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(paymentDayEndMapper);

        com.abc123.hsp.dto.PaymentDayEndAutoRunResultDTO result = service.runAutomatically();

        org.junit.jupiter.api.Assertions.assertFalse(result.getExecuted());
        org.junit.jupiter.api.Assertions.assertEquals("EXISTING_BATCH", result.getSkipReason());
        org.junit.jupiter.api.Assertions.assertEquals(expectedBizDate.toString(), result.getBizDate());
        org.junit.jupiter.api.Assertions.assertNull(result.getBatchNo());
        org.junit.jupiter.api.Assertions.assertNull(result.getBatchStatus());
        verify(paymentDayEndMapper, org.mockito.Mockito.never()).insertBatch(org.mockito.ArgumentMatchers.any());
        verify(paymentDayEndMapper, org.mockito.Mockito.never()).countPaymentsByDate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldSkipAutomaticDayEndWhenBusinessDateLeaseIsBusy() {
        LocalDate expectedBizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        PaymentTaskLeaseService leaseService = org.mockito.Mockito.mock(PaymentTaskLeaseService.class);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, (org.springframework.transaction.support.TransactionTemplate) null);
        when(leaseService.tryAcquire(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + expectedBizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-scheduler:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(null);

        com.abc123.hsp.dto.PaymentDayEndAutoRunResultDTO result = service.runAutomatically();

        org.junit.jupiter.api.Assertions.assertFalse(result.getExecuted());
        org.junit.jupiter.api.Assertions.assertEquals("LEASE_BUSY", result.getSkipReason());
        org.junit.jupiter.api.Assertions.assertEquals(expectedBizDate.toString(), result.getBizDate());
        org.junit.jupiter.api.Assertions.assertNull(result.getBatchNo());
        org.junit.jupiter.api.Assertions.assertNull(result.getBatchStatus());
        org.mockito.Mockito.verifyNoInteractions(paymentDayEndMapper);
    }

    @Test
    void shouldInspectPreviousBusinessDayBatchCounts() {
        String bizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        when(paymentDayEndMapper.countBatchesByDate(bizDate)).thenReturn(3);
        when(paymentDayEndMapper.countAutoBatchesByDate(bizDate)).thenReturn(2);

        com.abc123.hsp.dto.PaymentDayEndPatrolResultDTO result =
                new PaymentDayEndServiceImpl(paymentDayEndMapper).inspectPreviousBusinessDay();

        org.junit.jupiter.api.Assertions.assertTrue(result.getInspected());
        org.junit.jupiter.api.Assertions.assertNull(result.getSkipReason());
        org.junit.jupiter.api.Assertions.assertEquals(bizDate, result.getBizDate());
        org.junit.jupiter.api.Assertions.assertEquals(3, result.getBatchCount());
        org.junit.jupiter.api.Assertions.assertEquals(2, result.getAutoBatchCount());
    }

    @Test
    void shouldSkipDayEndPatrolWhenBusinessDateLeaseIsBusy() {
        String bizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        PaymentTaskLeaseService leaseService = org.mockito.Mockito.mock(PaymentTaskLeaseService.class);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, (org.springframework.transaction.support.TransactionTemplate) null);
        when(leaseService.tryAcquire(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + bizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-patrol-scheduler:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(null);

        com.abc123.hsp.dto.PaymentDayEndPatrolResultDTO result = service.inspectPreviousBusinessDay();

        org.junit.jupiter.api.Assertions.assertFalse(result.getInspected());
        org.junit.jupiter.api.Assertions.assertEquals("LEASE_BUSY", result.getSkipReason());
        org.junit.jupiter.api.Assertions.assertEquals(bizDate, result.getBizDate());
        org.junit.jupiter.api.Assertions.assertNull(result.getBatchCount());
        org.junit.jupiter.api.Assertions.assertNull(result.getAutoBatchCount());
        org.mockito.Mockito.verifyNoInteractions(paymentDayEndMapper);
    }

    @Test
    void shouldReleaseBusinessDateLeaseAfterDayEndPatrol() {
        String bizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).toString();
        PaymentTaskCenterMapper leaseMapper = org.mockito.Mockito.mock(PaymentTaskCenterMapper.class);
        when(leaseMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + bizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-patrol-scheduler:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(1);
        when(paymentDayEndMapper.countBatchesByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.countAutoBatchesByDate(bizDate)).thenReturn(1);
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper,
                new PaymentTaskLeaseService(leaseMapper),
                (org.springframework.transaction.support.TransactionTemplate) null);

        service.inspectPreviousBusinessDay();

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(paymentDayEndMapper, leaseMapper);
        inOrder.verify(paymentDayEndMapper).countBatchesByDate(bizDate);
        inOrder.verify(paymentDayEndMapper).countAutoBatchesByDate(bizDate);
        inOrder.verify(leaseMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + bizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-patrol-scheduler:"));
    }

    @Test
    void shouldRollbackFailedAutomaticBatchBeforeReleasingBusinessDateLease() {
        LocalDate expectedBizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        PaymentTaskCenterMapper leaseMapper = org.mockito.Mockito.mock(PaymentTaskCenterMapper.class);
        PaymentTaskLeaseService leaseService = new PaymentTaskLeaseService(leaseMapper);
        org.springframework.transaction.PlatformTransactionManager transactionManager =
                org.mockito.Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class);
        org.springframework.transaction.TransactionStatus transactionStatus =
                org.mockito.Mockito.mock(org.springframework.transaction.TransactionStatus.class);
        when(leaseMapper.acquireTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + expectedBizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-scheduler:"),
                org.mockito.ArgumentMatchers.eq(300))).thenReturn(1);
        when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
        when(paymentDayEndMapper.countBatchesByDate(expectedBizDate.toString())).thenReturn(0);
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed"))
                .when(paymentDayEndMapper).insertBatch(org.mockito.ArgumentMatchers.any());
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(
                paymentDayEndMapper, leaseService, transactionManager);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, service::runAutomatically);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                paymentDayEndMapper, transactionManager, leaseMapper);
        inOrder.verify(paymentDayEndMapper).insertBatch(org.mockito.ArgumentMatchers.any());
        inOrder.verify(transactionManager).rollback(transactionStatus);
        inOrder.verify(leaseMapper).releaseTaskLease(
                org.mockito.ArgumentMatchers.eq("PAYMENT_DAY_END:" + expectedBizDate),
                org.mockito.ArgumentMatchers.startsWith("payment-day-end-scheduler:"));
    }

    @Test
    void shouldWarnWhenOnlyPaymentSuccessGapRemains() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countPaymentSuccessGapByDate(bizDate)).thenReturn(1);
        when(paymentDayEndMapper.sumPaymentSuccessGapAmountByDate(bizDate)).thenReturn(new BigDecimal("268.00"));
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());

        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals("warn", captor.getValue().getBatchStatusType());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), captor.getValue().getPaymentSuccessGapCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment().contains("成功差异1笔"));
        org.junit.jupiter.api.Assertions.assertFalse(captor.getValue().getSummaryComment().contains("可进入次日对账"));
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(0), captor.getValue().getChannelAbnormalCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(0), captor.getValue().getInternalAbnormalCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(0), captor.getValue().getPendingRefundCount());
    }

    @Test
    void shouldWarnAndBlockReconciliationWhenPendingPaymentsRemain() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countPendingPaymentsByDate(bizDate)).thenReturn(2);
        when(paymentDayEndMapper.sumPendingPaymentAmountByDate(bizDate)).thenReturn(new BigDecimal("188.00"));
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(2);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), captor.getValue().getPendingPaymentCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment().contains("待收口支付2笔（¥188.00）"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), result.getAlerts().get(0).getAffectedCount());
    }

    @Test
    void shouldTreatLegacyBatchWithoutPendingPaymentFactAsIncomplete() {
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate("2026-07-18");
        PaymentDayEndBatchListItemDTO legacyBatch = new PaymentDayEndBatchListItemDTO();
        legacyBatch.setBatchNo("DEB-LEGACY");
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Collections.singletonList(legacyBatch));

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        org.junit.jupiter.api.Assertions.assertEquals("日终事实不完整", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertTrue(result.getReconciliationReadinessSummary()
                .contains("未完整采集待收口支付、成功时间、退款失败或支付退款终态一致性事实"));
        org.junit.jupiter.api.Assertions.assertEquals("UNKNOWN", result.getRecentBatches().get(0).getReconciliationReadinessStatus());
    }

    @Test
    void shouldWarnWhenSuccessfulPaymentTimeIsMissing() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countMissingPaymentSuccessTimeByDate(bizDate)).thenReturn(1);
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(1);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1), captor.getValue().getMissingPaymentSuccessTimeCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment().contains("支付成功时间缺失1笔"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), result.getAlerts().get(1).getAffectedCount());
    }

    @Test
    void shouldWarnWhenPaymentSuccessFactsMismatch() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countPaymentSuccessFactMismatchByDate(bizDate)).thenReturn(1);
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(1);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1), captor.getValue().getPaymentSuccessFactMismatchCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment()
                .contains("支付成功事实不一致1笔"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), result.getAlerts().get(2).getAffectedCount());
    }

    @Test
    void shouldWarnWhenSuccessfulRefundTimeIsMissing() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countMissingRefundSuccessTimeByDate(bizDate)).thenReturn(1);
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(1);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1), captor.getValue().getMissingRefundSuccessTimeCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment()
                .contains("退款成功时间缺失1笔"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), result.getAlerts().get(6).getAffectedCount());
    }

    @Test
    void shouldWarnWhenRefundTerminalFactsMismatch() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countRefundTerminalFactMismatchByDate(bizDate)).thenReturn(1);
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(1);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(
                Integer.valueOf(1), captor.getValue().getRefundTerminalFactMismatchCount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment()
                .contains("退款终态事实不一致1笔"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), result.getAlerts().get(7).getAffectedCount());
    }

    @Test
    void shouldWarnAndBlockReconciliationWhenRefundsFail() {
        String bizDate = "2026-07-19";
        when(paymentDayEndMapper.countFailedRefundsByDate(bizDate)).thenReturn(2);
        when(paymentDayEndMapper.sumFailedRefundAmountByDate(bizDate)).thenReturn(new BigDecimal("88.00"));
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate(bizDate);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(2);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches())
                .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
        PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
        request.setBizDate(bizDate);
        request.setTriggeredBy("finance-ops");

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

        ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
        verify(paymentDayEndMapper).insertBatch(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("WARNING", captor.getValue().getBatchStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), captor.getValue().getRefundFailCount());
        org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("88.00"), captor.getValue().getRefundFailAmount());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getSummaryComment()
                .contains("退款失败2笔（¥88.00）"));
        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), result.getAlerts().get(8).getAffectedCount());
        org.junit.jupiter.api.Assertions.assertEquals(
                "/refunds?refundStatus=FAIL", result.getAlerts().get(8).getActionRoute());
    }

    @Test
    void shouldResolveDefaultBizDateInBusinessTimezone() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate businessToday = LocalDate.now(businessZone);
        ZoneId alternativeZone = ZoneId.of("Pacific/Kiritimati");
        if (businessToday.equals(LocalDate.now(alternativeZone))) {
            alternativeZone = ZoneId.of("Etc/GMT+12");
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(businessToday, LocalDate.now(alternativeZone));

        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(alternativeZone));
            when(paymentDayEndMapper.findRecentBatches())
                    .thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());
            PaymentDayEndRunRequestDTO request = new PaymentDayEndRunRequestDTO();
            request.setTriggeredBy("finance-ops");

            new PaymentDayEndServiceImpl(paymentDayEndMapper).run(request);

            ArgumentCaptor<com.abc123.hsp.entity.PaymentDayEndBatchEntity> captor =
                    ArgumentCaptor.forClass(com.abc123.hsp.entity.PaymentDayEndBatchEntity.class);
            verify(paymentDayEndMapper).insertBatch(captor.capture());
            org.junit.jupiter.api.Assertions.assertEquals(
                    businessToday.minusDays(1).toString(), captor.getValue().getBizDate());
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void shouldRejectInvalidManualTriggerBeforeReadingDayEndFacts() {
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(paymentDayEndMapper);
        PaymentDayEndRunRequestDTO blankOperator = new PaymentDayEndRunRequestDTO();
        blankOperator.setTriggeredBy("   ");
        PaymentDayEndRunRequestDTO oversizedOperator = new PaymentDayEndRunRequestDTO();
        oversizedOperator.setTriggeredBy(String.join("", Collections.nCopies(65, "A")));
        PaymentDayEndRunRequestDTO controlOperator = new PaymentDayEndRunRequestDTO();
        controlOperator.setTriggeredBy("finance-ops\u0085");
        PaymentDayEndRunRequestDTO automaticMode = new PaymentDayEndRunRequestDTO();
        automaticMode.setTriggeredBy("finance-ops");
        automaticMode.setRunMode("AUTO");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(null));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(blankOperator));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(oversizedOperator));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(controlOperator));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(automaticMode));

        org.mockito.Mockito.verifyNoInteractions(paymentDayEndMapper);
    }

    @Test
    void shouldRejectOpenBusinessDatesBeforeReadingDayEndFacts() {
        LocalDate businessToday = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        PaymentDayEndRunRequestDTO currentDate = new PaymentDayEndRunRequestDTO();
        currentDate.setBizDate(businessToday.toString());
        currentDate.setTriggeredBy("finance-ops");
        PaymentDayEndRunRequestDTO futureDate = new PaymentDayEndRunRequestDTO();
        futureDate.setBizDate(businessToday.plusDays(1).toString());
        futureDate.setTriggeredBy("finance-ops");
        PaymentDayEndRunRequestDTO invalidCalendarDate = new PaymentDayEndRunRequestDTO();
        invalidCalendarDate.setBizDate("2026-02-30");
        invalidCalendarDate.setTriggeredBy("finance-ops");
        PaymentDayEndServiceImpl service = new PaymentDayEndServiceImpl(paymentDayEndMapper);

        IllegalArgumentException currentDateError = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(currentDate));
        IllegalArgumentException futureDateError = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(futureDate));
        IllegalArgumentException invalidCalendarDateError = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.run(invalidCalendarDate));

        org.junit.jupiter.api.Assertions.assertEquals("业务日期必须早于上海当前日期", currentDateError.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals("业务日期必须早于上海当前日期", futureDateError.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(
                "业务日期格式必须为 yyyy-MM-dd", invalidCalendarDateError.getMessage());
        org.mockito.Mockito.verifyNoInteractions(paymentDayEndMapper);
    }

    @Test
    void shouldBlockReconciliationWhenPrimaryFactsAreNotClosed() {
        PaymentDayEndOverviewDTO overview = new PaymentDayEndOverviewDTO();
        overview.setLatestBizDate("2026-07-20");
        overview.setLatestBatchStatus("WARNING");
        overview.setOpenChannelAbnormalCount(2);
        overview.setOpenInternalAbnormalCount(1);
        overview.setLatestPaymentSuccessGapCount(3);
        overview.setOpenPendingPaymentCount(0);
        overview.setOpenMissingPaymentSuccessTimeCount(0);
        overview.setOpenPaymentSuccessFactMismatchCount(0);
        overview.setOpenRefundFailCount(0);
        overview.setOpenMissingRefundSuccessTimeCount(0);
        overview.setOpenRefundTerminalFactMismatchCount(0);
        overview.setOpenPendingRefundCount(0);
        when(paymentDayEndMapper.findOverviewSummary()).thenReturn(overview);
        when(paymentDayEndMapper.findRecentBatches()).thenReturn(Collections.<PaymentDayEndBatchListItemDTO>emptyList());

        PaymentDayEndOverviewDTO result = new PaymentDayEndServiceImpl(paymentDayEndMapper).overview();

        org.junit.jupiter.api.Assertions.assertEquals("禁止进入对账", result.getReconciliationReadinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals("danger", result.getReconciliationReadinessType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getReconciliationReadinessSummary().contains("支付成功差异 3 笔"));
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(3), result.getAlerts().get(3).getAffectedCount());
        org.junit.jupiter.api.Assertions.assertEquals("P1", result.getAlerts().get(3).getSeverityLevel());
    }
}
