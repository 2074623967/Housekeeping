package com.abc123.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.reconciliation.common.BusinessException;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.CrossSystemChainSnapshotDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchDetailDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.service.ReconciliationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证 DDL、Mapper 和自动匹配规则。
 */
@SpringBootTest
class ReconciliationIntegrationTest {

    @Autowired
    private ReconciliationService service;

    @Test
    void shouldMatchAndDetectAmountDifference() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-04");
        request.setChannelCode("WECHAT");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        ChannelRecordRequestDTO channel = new ChannelRecordRequestDTO();
        channel.setChannelTradeNo("WX-IT-1");
        channel.setPaymentOrderId("PAY-IT-1");
        channel.setAmount(new BigDecimal("100.00"));
        service.addChannelRecord(batch.getBatchNo(), channel);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-1");
        internal.setAmount(new BigDecimal("99.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);
        recordCompleteChain("PAY-IT-1");

        ReconciliationBatchListItemDTO result = service.run(batch.getBatchNo());
        assertEquals(1, result.getDifferenceCount());
        assertEquals(0, result.getMatchedCount());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("READY", result.getReadinessStatus());
    }

    @Test
    void shouldResolveOpenDifferenceOnlyOnce() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-04");
        request.setChannelCode("ALIPAY");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        ChannelRecordRequestDTO channel = new ChannelRecordRequestDTO();
        channel.setChannelTradeNo("ALI-IT-2");
        channel.setPaymentOrderId("PAY-IT-2");
        channel.setAmount(new BigDecimal("88.00"));
        service.addChannelRecord(batch.getBatchNo(), channel);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-2");
        internal.setAmount(new BigDecimal("66.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);
        recordCompleteChain("PAY-IT-2");

        service.run(batch.getBatchNo());

        DifferenceQueryDTO query = new DifferenceQueryDTO();
        query.setBatchNo(batch.getBatchNo());
        PageResultDTO<ReconciliationDifferenceDTO> pageResult = service.differences(query);
        assertEquals(1, pageResult.getTotal());
        ReconciliationDifferenceDTO difference = pageResult.getItems().get(0);

        DifferenceResolveRequestDTO resolveRequest = new DifferenceResolveRequestDTO();
        resolveRequest.setDifferenceNo(difference.getDifferenceNo());
        resolveRequest.setResolution("人工核实已调账");
        resolveRequest.setRemark("集成测试结案");
        service.resolve(resolveRequest);

        assertThrows(BusinessException.class, () -> service.resolve(resolveRequest));
    }

    @Test
    void shouldReturnBatchDetailWorkbenchView() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-05");
        request.setChannelCode("UNIONPAY");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        ChannelRecordRequestDTO channel = new ChannelRecordRequestDTO();
        channel.setChannelTradeNo("UP-IT-3");
        channel.setPaymentOrderId("PAY-IT-3");
        channel.setAmount(new BigDecimal("168.00"));
        service.addChannelRecord(batch.getBatchNo(), channel);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-3");
        internal.setAmount(new BigDecimal("168.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);
        recordCompleteChain("PAY-IT-3");

        ChannelRecordRequestDTO channelOnly = new ChannelRecordRequestDTO();
        channelOnly.setChannelTradeNo("UP-IT-4");
        channelOnly.setPaymentOrderId("PAY-IT-4");
        channelOnly.setAmount(new BigDecimal("88.00"));
        service.addChannelRecord(batch.getBatchNo(), channelOnly);

        service.run(batch.getBatchNo());

        ReconciliationBatchDetailDTO detail = service.detail(batch.getBatchNo());
        assertEquals(batch.getBatchNo(), detail.getBatchNo());
        assertEquals(2, detail.getChannelRecords().size());
        assertEquals(1, detail.getInternalRecords().size());
        assertEquals(1, detail.getMatchedCount());
        assertEquals(1, detail.getDifferenceCount());
        assertEquals(1, detail.getOpenDifferenceCount());
        assertEquals(0, detail.getResolvedDifferenceCount());
    }

    @Test
    void shouldExportBatchDetailSnapshot() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-06");
        request.setChannelCode("WECHAT");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        ChannelRecordRequestDTO channel = new ChannelRecordRequestDTO();
        channel.setChannelTradeNo("WX-IT-5");
        channel.setPaymentOrderId("PAY-IT-5");
        channel.setAmount(new BigDecimal("50.00"));
        service.addChannelRecord(batch.getBatchNo(), channel);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-5");
        internal.setAmount(new BigDecimal("40.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);
        recordCompleteChain("PAY-IT-5");

        service.run(batch.getBatchNo());

        String csv = new String(service.exportBatchDetail(batch.getBatchNo()), StandardCharsets.UTF_8);
        assertEquals(true, csv.contains("批次号,业务日期,渠道编码"));
        assertEquals(true, csv.contains(batch.getBatchNo()));
        assertEquals(true, csv.contains("渠道账单记录"));
        assertEquals(true, csv.contains("平台支付事实"));
        assertEquals(true, csv.contains("差异明细"));
    }

    @Test
    void shouldBuildIdempotentCrossSystemChainSnapshot() {
        String chainId = "CHAIN-IT-001";
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-CHAIN-001", "SUCCESS");
        recordFact(chainId, "clearing-system", "CLEARING_GENERATED", "CLR-CHAIN-001", "SUCCESS");
        recordFact(chainId, "settlement-system", "SETTLEMENT_CREATED", "SET-CHAIN-001", "SUCCESS");
        recordFact(chainId, "accounting-system", "ACCOUNTING_POSTED", "ACC-CHAIN-001", "SUCCESS");

        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain(chainId);
        assertEquals(4, snapshot.getFactCount());
        assertEquals(false, snapshot.isHasAbnormalFact());
        assertEquals("支付、清分、结算、账务关键事实已完整收口", snapshot.getDiagnosis());
        assertEquals("COMPLETED", snapshot.getChainStatus());
        assertEquals("CLOSED", snapshot.getResponsibilityStatus());
        assertEquals("允许进入财务收口并归档链路事实", snapshot.getNextAction());
        assertEquals(4, snapshot.getCompletedStageCount());
        assertEquals(4, snapshot.getExpectedStageCount());
        assertEquals(true, snapshot.isFinancialCloseable());

        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-CHAIN-001", "SUCCESS");
        assertEquals(4, service.crossSystemChain(chainId).getFactCount());
    }

    @Test
    void shouldUpgradeExistingFactStatusOnIdempotentRetry() {
        String chainId = "CHAIN-IT-RETRY-001";
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-RETRY-001", "PENDING");
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-RETRY-001", "SUCCESS");

        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain(chainId);

        assertEquals(1, snapshot.getFactCount());
        assertEquals("SUCCESS", snapshot.getFacts().get(0).getFactStatus());
        assertEquals(1, snapshot.getCompletedStageCount());
        assertEquals("IN_PROGRESS", snapshot.getChainStatus());
    }

    @Test
    void shouldBlockFinancialCloseWhenChainHasAbnormalFact() {
        String chainId = "CHAIN-IT-002";
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-CHAIN-002", "SUCCESS");
        recordFact(chainId, "settlement-system", "PAYOUT", "POU-CHAIN-002", "FAILED");

        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain(chainId);
        assertEquals(2, snapshot.getFactCount());
        assertEquals(true, snapshot.isHasAbnormalFact());
        assertEquals("链路存在失败或异常事实，禁止直接进入财务收口", snapshot.getDiagnosis());
        assertEquals("EXCEPTION", snapshot.getChainStatus());
        assertEquals("MANUAL_REVIEW", snapshot.getResponsibilityStatus());
        assertEquals("创建或重试补偿任务，完成异常事实人工复核", snapshot.getNextAction());
        assertEquals(false, snapshot.isFinancialCloseable());
    }

    @Test
    void shouldExposeInProgressChainStateAndNextAction() {
        String chainId = "CHAIN-IT-003";
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-CHAIN-003", "SUCCESS");

        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain(chainId);

        assertEquals("IN_PROGRESS", snapshot.getChainStatus());
        assertEquals("PENDING", snapshot.getResponsibilityStatus());
        assertEquals("等待 clearing-system 上报 CLEARING_GENERATED", snapshot.getNextAction());
        assertEquals(1, snapshot.getCompletedStageCount());
        assertEquals(false, snapshot.isFinancialCloseable());
    }

    @Test
    void shouldExposeWaitingStateWhenChainHasNoFact() {
        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain("CHAIN-IT-EMPTY");

        assertEquals("WAITING", snapshot.getChainStatus());
        assertEquals("UNASSIGNED", snapshot.getResponsibilityStatus());
        assertEquals("等待 payment-core 上报 PAYMENT_SUCCESS", snapshot.getNextAction());
        assertEquals(0, snapshot.getFactCount());
        assertEquals(false, snapshot.isFinancialCloseable());
    }

    @Test
    void shouldNotTreatPendingFactAsCompletedStage() {
        String chainId = "CHAIN-IT-PENDING";
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", "PAY-CHAIN-PENDING", "PENDING");

        CrossSystemChainSnapshotDTO snapshot = service.crossSystemChain(chainId);

        assertEquals("IN_PROGRESS", snapshot.getChainStatus());
        assertEquals(0, snapshot.getCompletedStageCount());
        assertEquals("等待 payment-core 上报 PAYMENT_SUCCESS", snapshot.getNextAction());
        assertEquals(false, snapshot.isFinancialCloseable());
    }

    @Test
    void shouldBlockReconciliationWhenBatchChainIsNotReady() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-07");
        request.setChannelCode("WECHAT");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-NOT-READY");
        internal.setAmount(new BigDecimal("20.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);

        assertThrows(BusinessException.class, () -> service.run(batch.getBatchNo()));

        ReconciliationBatchListItemDTO checked = service.checkBatchReadiness(batch.getBatchNo());
        assertEquals("CONDITIONAL", checked.getReadinessStatus());
        assertEquals(1, checked.getReadinessChainCount());
        assertEquals(0, checked.getReadinessCompletedChainCount());
        assertEquals(1, checked.getReadinessPendingChainCount());
        assertEquals(false, "READY".equals(checked.getReadinessStatus()));
    }

    @Test
    void shouldBlockReconciliationWhenBatchContainsAbnormalChain() {
        ReconciliationBatchCreateRequestDTO request = new ReconciliationBatchCreateRequestDTO();
        request.setBusinessDate("2026-08-07");
        request.setChannelCode("ALIPAY");
        ReconciliationBatchListItemDTO batch = service.createBatch(request);

        InternalRecordRequestDTO internal = new InternalRecordRequestDTO();
        internal.setPaymentOrderId("PAY-IT-ABNORMAL");
        internal.setAmount(new BigDecimal("30.00"));
        service.addInternalRecord(batch.getBatchNo(), internal);
        recordFact("CHAIN-PAY-IT-ABNORMAL", "payment-core", "PAYMENT_SUCCESS",
                "PAY-IT-ABNORMAL", "SUCCESS");
        recordFact("CHAIN-PAY-IT-ABNORMAL", "clearing-system", "CLEARING_GENERATED",
                "CLR-IT-ABNORMAL", "FAILED");

        assertThrows(BusinessException.class, () -> service.run(batch.getBatchNo()));

        ReconciliationBatchListItemDTO checked = service.checkBatchReadiness(batch.getBatchNo());
        assertEquals("BLOCKED", checked.getReadinessStatus());
        assertEquals(1, checked.getReadinessAbnormalChainCount());
        assertEquals("reconciliation-ops", checked.getReadinessBlockingOwner());
    }

    private void recordCompleteChain(String paymentOrderId) {
        String chainId = "CHAIN-" + paymentOrderId;
        recordFact(chainId, "payment-core", "PAYMENT_SUCCESS", paymentOrderId, "SUCCESS");
        recordFact(chainId, "clearing-system", "CLEARING_GENERATED",
                "CLR-" + paymentOrderId, "SUCCESS");
        recordFact(chainId, "settlement-system", "SETTLEMENT_CREATED",
                "SET-" + paymentOrderId, "SUCCESS");
        recordFact(chainId, "accounting-system", "ACCOUNTING_POSTED",
                "ACC-" + paymentOrderId, "SUCCESS");
    }

    private void recordFact(String chainId, String systemCode, String factType,
                            String factNo, String factStatus) {
        CrossSystemFactRequestDTO request = new CrossSystemFactRequestDTO();
        request.setBusinessChainId(chainId);
        request.setSystemCode(systemCode);
        request.setFactType(factType);
        request.setFactNo(factNo);
        request.setBusinessNo(factNo);
        request.setFactStatus(factStatus);
        request.setAmount(new BigDecimal("168.00"));
        service.recordCrossSystemFact(request);
    }
}
