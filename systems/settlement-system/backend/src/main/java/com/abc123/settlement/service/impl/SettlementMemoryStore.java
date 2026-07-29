package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.entity.PayoutBatchEntity;
import com.abc123.settlement.entity.PayoutRecordEntity;
import com.abc123.settlement.entity.SettlementAuditLogEntity;
import com.abc123.settlement.entity.SettlementBatchEntity;
import com.abc123.settlement.entity.SettlementEventEntity;
import com.abc123.settlement.entity.SettlementItemEntity;
import com.abc123.settlement.entity.SettlementOrderEntity;
import com.abc123.settlement.mapper.SettlementDataMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 结算系统持久化数据仓，用于第一版正式版联调。
 */
@Component
public class SettlementMemoryStore {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong batchSeq = new AtomicLong(10000L);
    private final AtomicLong orderSeq = new AtomicLong(20000L);
    private final AtomicLong itemSeq = new AtomicLong(30000L);
    private final AtomicLong auditSeq = new AtomicLong(40000L);
    private final AtomicLong payoutSeq = new AtomicLong(50000L);
    private final AtomicLong payoutRecordSeq = new AtomicLong(60000L);
    private final AtomicLong eventSeq = new AtomicLong(70000L);
    private final SettlementDataMapper settlementDataMapper;

    public SettlementMemoryStore(SettlementDataMapper settlementDataMapper) {
        this.settlementDataMapper = settlementDataMapper;
    }

    @PostConstruct
    @Transactional
    public void initDemoData() {
        if (settlementDataMapper.countBatches() > 0) {
            syncSequencesFromDatabase();
            return;
        }
        SettlementBatchEntity batch = createBatch("2026-07-20", "MANUAL", "结算运营", "SETTLE-20260720-INIT");
        SettlementOrderEntity workerOrder = createOrder(batch.getBatchNo(), "WORKER", "WRK1001", "李阿姨", new BigDecimal("120.00"), new BigDecimal("8.00"), "待审核", "CLO20001");
        SettlementOrderEntity merchantOrder = createOrder(batch.getBatchNo(), "MERCHANT", "MCH1001", "上海静安门店", new BigDecimal("20.00"), new BigDecimal("0.00"), "待审核", "CLO20001");
        createItem(workerOrder.getSettlementNo(), "基础结算", "应结金额", new BigDecimal("120.00"));
        createItem(workerOrder.getSettlementNo(), "结算扣减", "扣减金额", new BigDecimal("8.00"));
        createItem(merchantOrder.getSettlementNo(), "商家结算", "应结金额", new BigDecimal("20.00"));
        createAuditLog(workerOrder.getSettlementNo(), "创建结算单", "待审核", "系统");
        createAuditLog(merchantOrder.getSettlementNo(), "创建结算单", "待审核", "系统");
        updateBatchSummary(batch.getBatchNo(), "待审核", null);
        consumeClearingGenerated(buildDemoEvent());
    }

    public List<SettlementBatchEntity> batches() {
        List<SettlementBatchEntity> items = new ArrayList<>(settlementDataMapper.findBatches());
        items.sort(Comparator.comparing(SettlementBatchEntity::getCreatedAt).reversed());
        return items;
    }

    public SettlementBatchEntity findBatch(String batchNo) {
        return settlementDataMapper.findBatch(batchNo);
    }

    @Transactional
    public SettlementBatchEntity createBatch(String batchDate, String settlementType, String createdBy, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            SettlementBatchEntity existingBatch = settlementDataMapper.findBatchByIdempotencyKey(idempotencyKey);
            if (existingBatch != null) {
                return existingBatch;
            }
        }
        SettlementBatchEntity entity = new SettlementBatchEntity();
        entity.setBatchNo(nextNo("SET", batchSeq));
        entity.setBatchDate(batchDate);
        entity.setSettlementType(settlementType);
        entity.setBatchStatus("处理中");
        entity.setTotalCount(0);
        entity.setAuditedCount(0);
        entity.setPayoutCount(0);
        entity.setTotalAmount(BigDecimal.ZERO);
        entity.setPayoutChannel("BANK");
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now());
        entity.setIdempotencyKey(idempotencyKey);
        settlementDataMapper.insertBatch(entity);
        return entity;
    }

    public List<SettlementOrderEntity> orders() {
        List<SettlementOrderEntity> items = new ArrayList<>(settlementDataMapper.findOrders());
        items.sort(Comparator.comparing(SettlementOrderEntity::getCreatedAt).reversed());
        return items;
    }

    public SettlementOrderEntity findOrder(String settlementNo) {
        return settlementDataMapper.findOrder(settlementNo);
    }

    @Transactional
    public SettlementOrderEntity createOrder(String batchNo, String targetType, String targetNo, String targetName,
            BigDecimal shouldSettleAmount, BigDecimal deductAmount, String settlementStatus, String clearingNo) {
        SettlementOrderEntity entity = new SettlementOrderEntity();
        entity.setSettlementNo(nextNo("SLT", orderSeq));
        entity.setBatchNo(batchNo);
        entity.setTargetType(targetType);
        entity.setTargetNo(targetNo);
        entity.setTargetName(targetName);
        entity.setShouldSettleAmount(shouldSettleAmount);
        entity.setDeductAmount(deductAmount);
        entity.setNetSettleAmount(shouldSettleAmount.subtract(deductAmount).setScale(2, RoundingMode.HALF_UP));
        entity.setSettlementStatus(settlementStatus);
        entity.setPayoutStatus("待出款");
        entity.setAuditStatus("待审核");
        entity.setCreatedAt(now());
        entity.setClearingNo(clearingNo);
        settlementDataMapper.insertOrder(entity);
        updateBatchSummary(batchNo);
        return entity;
    }

    public List<SettlementItemEntity> itemsBySettlementNo(String settlementNo) {
        return settlementDataMapper.findItemsBySettlementNo(settlementNo);
    }

    @Transactional
    public SettlementItemEntity createItem(String settlementNo, String itemType, String itemName, BigDecimal itemAmount) {
        SettlementItemEntity entity = new SettlementItemEntity();
        entity.setItemNo(nextNo("ITM", itemSeq));
        entity.setSettlementNo(settlementNo);
        entity.setItemType(itemType);
        entity.setItemName(itemName);
        entity.setItemAmount(itemAmount);
        entity.setCreatedAt(now());
        settlementDataMapper.insertItem(entity);
        return entity;
    }

    public List<SettlementAuditLogEntity> auditLogsBySettlementNo(String settlementNo) {
        return settlementDataMapper.findAuditLogsBySettlementNo(settlementNo);
    }

    @Transactional
    public SettlementAuditLogEntity createAuditLog(String settlementNo, String action, String result, String operatorName) {
        return createAuditLog(settlementNo, action, result, operatorName, "");
    }

    @Transactional
    public SettlementAuditLogEntity createAuditLog(String settlementNo, String action, String result, String operatorName, String remark) {
        SettlementAuditLogEntity entity = new SettlementAuditLogEntity();
        entity.setAuditNo(nextNo("AUD", auditSeq));
        entity.setSettlementNo(settlementNo);
        entity.setAuditAction(action);
        entity.setAuditResult(result);
        entity.setOperatorName(operatorName);
        entity.setRemark(remark);
        entity.setCreatedAt(now());
        settlementDataMapper.insertAuditLog(entity);
        return entity;
    }

    public List<PayoutBatchEntity> payoutBatches() {
        List<PayoutBatchEntity> items = new ArrayList<>(settlementDataMapper.findPayoutBatches());
        items.sort(Comparator.comparing(PayoutBatchEntity::getCreatedAt).reversed());
        return items;
    }

    public PayoutBatchEntity findPayoutBatch(String payoutBatchNo) {
        return settlementDataMapper.findPayoutBatch(payoutBatchNo);
    }

    @Transactional
    public PayoutBatchEntity createPayoutBatch(String batchNo, String payoutChannel, String createdBy) {
        PayoutBatchEntity entity = new PayoutBatchEntity();
        entity.setPayoutBatchNo(nextNo("PBT", payoutSeq));
        entity.setBatchNo(batchNo);
        entity.setPayoutChannel(payoutChannel);
        entity.setPayoutStatus("处理中");
        entity.setPayoutCount(0);
        entity.setSuccessCount(0);
        entity.setFailedCount(0);
        entity.setTotalAmount(BigDecimal.ZERO);
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now());
        settlementDataMapper.insertPayoutBatch(entity);
        return entity;
    }

    public List<PayoutRecordEntity> payoutRecordsByBatchNo(String payoutBatchNo) {
        return settlementDataMapper.findPayoutRecordsByBatchNo(payoutBatchNo);
    }

    @Transactional
    public PayoutRecordEntity createPayoutRecord(String payoutBatchNo, SettlementOrderEntity order) {
        PayoutRecordEntity entity = new PayoutRecordEntity();
        entity.setPayoutNo(nextNo("POU", payoutRecordSeq));
        entity.setPayoutBatchNo(payoutBatchNo);
        entity.setSettlementNo(order.getSettlementNo());
        entity.setTargetNo(order.getTargetNo());
        entity.setTargetName(order.getTargetName());
        entity.setPayoutAmount(order.getNetSettleAmount());
        entity.setPayoutStatus("已发放");
        entity.setRetryCount(0);
        entity.setCreatedAt(now());
        settlementDataMapper.insertPayoutRecord(entity);
        settlementDataMapper.updateOrderStatus(order.getSettlementNo(), "已出款", "已发放", "已通过");
        updatePayoutBatchSummary(payoutBatchNo);
        updateBatchSummary(findPayoutBatch(payoutBatchNo).getBatchNo());
        return entity;
    }

    public List<SettlementEventEntity> events() {
        List<SettlementEventEntity> items = new ArrayList<>(settlementDataMapper.findEvents());
        items.sort(Comparator.comparing(SettlementEventEntity::getCreatedAt).reversed());
        return items;
    }

    @Transactional
    public SettlementEventEntity consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        SettlementBatchEntity batch = createBatch("2026-07-20", "EVENT", "系统事件", "EVENT-" + request.getClearingNo());
        SettlementOrderEntity order = createOrder(
                batch.getBatchNo(),
                request.getTargetType(),
                request.getTargetNo(),
                request.getTargetName(),
                request.getShouldSettleAmount(),
                request.getDeductAmount(),
                "待审核",
                request.getClearingNo());
        createItem(order.getSettlementNo(), "应结金额", "应结", request.getShouldSettleAmount());
        createItem(order.getSettlementNo(), "扣减金额", "扣减", request.getDeductAmount());
        updateBatchSummary(batch.getBatchNo(), "已完成", now());

        SettlementEventEntity entity = new SettlementEventEntity();
        entity.setEventNo(nextNo("SVE", eventSeq));
        entity.setEventType("CLEARING_GENERATED");
        entity.setBizNo(request.getClearingNo());
        entity.setSummary("清分结果生成后自动生成结算单");
        entity.setPayload("{\"clearingNo\":\"" + request.getClearingNo() + "\"}");
        entity.setEventStatus("已消费");
        entity.setCreatedAt(now());
        settlementDataMapper.insertEvent(entity);
        return entity;
    }

    @Transactional
    public SettlementOrderEntity audit(String settlementNo, String operatorName, String remark, boolean approved) {
        SettlementOrderEntity entity = findOrder(settlementNo);
        entity.setAuditStatus(approved ? "已通过" : "已退回");
        entity.setSettlementStatus(approved ? "待出款" : "已退回");
        settlementDataMapper.updateOrderStatus(settlementNo, entity.getSettlementStatus(), entity.getPayoutStatus(), entity.getAuditStatus());
        createAuditLog(settlementNo, approved ? "审核通过" : "审核退回", approved ? "通过" : "退回", operatorName, remark);
        SettlementBatchEntity batch = findBatch(entity.getBatchNo());
        if (approved) {
            batch.setAuditedCount(batch.getAuditedCount() + 1);
            batch.setBatchStatus("待出款");
        } else {
            batch.setBatchStatus("存在退回");
        }
        updateBatchSummary(batch.getBatchNo(), batch.getBatchStatus(), batch.getFinishedAt());
        return findOrder(settlementNo);
    }

    @Transactional
    public PayoutBatchEntity retryPayoutBatch(String payoutBatchNo, String operatorName, String reason) {
        PayoutBatchEntity entity = findPayoutBatch(payoutBatchNo);
        for (PayoutRecordEntity record : payoutRecordsByBatchNo(payoutBatchNo)) {
            if (!"已发放".equals(record.getPayoutStatus())) {
                settlementDataMapper.updatePayoutRecord(record.getPayoutNo(), "已发放", record.getRetryCount() + 1);
            }
        }
        settlementDataMapper.updatePayoutBatch(
                payoutBatchNo,
                "已完成",
                entity.getPayoutCount(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                entity.getTotalAmount(),
                now());
        updateBatchSummary(entity.getBatchNo());
        return findPayoutBatch(payoutBatchNo);
    }

    @Transactional
    public SettlementOrderEntity markOrderPayoutCompleted(String settlementNo) {
        SettlementOrderEntity entity = findOrder(settlementNo);
        settlementDataMapper.updateOrderStatus(settlementNo, "已出款", "已发放", entity.getAuditStatus());
        return findOrder(settlementNo);
    }

    private void updateBatchSummary(String batchNo) {
        SettlementBatchEntity batch = findBatch(batchNo);
        if (batch == null) {
            return;
        }
        updateBatchSummary(batchNo, batch.getBatchStatus(), batch.getFinishedAt());
    }

    private void updateBatchSummary(String batchNo, String batchStatus, String finishedAt) {
        SettlementBatchEntity batch = findBatch(batchNo);
        if (batch == null) {
            return;
        }
        List<SettlementOrderEntity> batchOrders = orders().stream()
                .filter(item -> batchNo.equals(item.getBatchNo()))
                .collect(Collectors.toList());
        int totalCount = batchOrders.size();
        int auditedCount = (int) batchOrders.stream()
                .filter(item -> "已通过".equals(item.getAuditStatus()))
                .count();
        int payoutCount = (int) batchOrders.stream()
                .filter(item -> "已发放".equals(item.getPayoutStatus()))
                .count();
        BigDecimal totalAmount = batchOrders.stream()
                .map(SettlementOrderEntity::getNetSettleAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        settlementDataMapper.updateBatch(
                batch.getBatchNo(),
                totalCount,
                auditedCount,
                payoutCount,
                totalAmount,
                batch.getPayoutChannel(),
                batchStatus,
                finishedAt);
    }

    private void updatePayoutBatchSummary(String payoutBatchNo) {
        PayoutBatchEntity batch = findPayoutBatch(payoutBatchNo);
        if (batch == null) {
            return;
        }
        List<PayoutRecordEntity> records = payoutRecordsByBatchNo(payoutBatchNo);
        int payoutCount = records.size();
        int successCount = (int) records.stream()
                .filter(item -> "已发放".equals(item.getPayoutStatus()))
                .count();
        int failedCount = (int) records.stream()
                .filter(item -> "已失败".equals(item.getPayoutStatus()))
                .count();
        BigDecimal totalAmount = records.stream()
                .map(PayoutRecordEntity::getPayoutAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String payoutStatus = failedCount > 0 ? "部分失败" : (payoutCount > 0 ? "已完成" : batch.getPayoutStatus());
        String finishedAt = payoutCount > 0 ? now() : batch.getFinishedAt();
        settlementDataMapper.updatePayoutBatch(
                batch.getPayoutBatchNo(),
                payoutStatus,
                payoutCount,
                successCount,
                failedCount,
                totalAmount,
                finishedAt);
    }

    private ClearingGeneratedEventRequestDTO buildDemoEvent() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO20001");
        request.setPaymentOrderId("PAY202607200001");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK1001");
        request.setTargetName("李阿姨");
        request.setShouldSettleAmount(new BigDecimal("120.00"));
        request.setDeductAmount(new BigDecimal("8.00"));
        request.setNetSettleAmount(new BigDecimal("112.00"));
        return request;
    }

    private void syncSequencesFromDatabase() {
        syncSequence(batchSeq, firstBatchNo());
        syncSequence(orderSeq, firstOrderNo());
        syncSequence(itemSeq, firstItemNo());
        syncSequence(auditSeq, firstAuditNo());
        syncSequence(payoutSeq, firstPayoutBatchNo());
        syncSequence(payoutRecordSeq, firstPayoutRecordNo());
        syncSequence(eventSeq, firstEventNo());
    }

    private String firstBatchNo() {
        List<SettlementBatchEntity> items = settlementDataMapper.findBatches();
        return items.isEmpty() ? null : items.get(0).getBatchNo();
    }

    private String firstOrderNo() {
        List<SettlementOrderEntity> items = settlementDataMapper.findOrders();
        return items.isEmpty() ? null : items.get(0).getSettlementNo();
    }

    private String firstItemNo() {
        String settlementNo = firstOrderNo();
        if (settlementNo == null) {
            return null;
        }
        List<SettlementItemEntity> items = settlementDataMapper.findItemsBySettlementNo(settlementNo);
        return items.isEmpty() ? null : items.get(0).getItemNo();
    }

    private String firstAuditNo() {
        String settlementNo = firstOrderNo();
        if (settlementNo == null) {
            return null;
        }
        List<SettlementAuditLogEntity> items = settlementDataMapper.findAuditLogsBySettlementNo(settlementNo);
        return items.isEmpty() ? null : items.get(0).getAuditNo();
    }

    private String firstPayoutBatchNo() {
        List<PayoutBatchEntity> items = settlementDataMapper.findPayoutBatches();
        return items.isEmpty() ? null : items.get(0).getPayoutBatchNo();
    }

    private String firstPayoutRecordNo() {
        String payoutBatchNo = firstPayoutBatchNo();
        if (payoutBatchNo == null) {
            return null;
        }
        List<PayoutRecordEntity> items = settlementDataMapper.findPayoutRecordsByBatchNo(payoutBatchNo);
        return items.isEmpty() ? null : items.get(0).getPayoutNo();
    }

    private String firstEventNo() {
        List<SettlementEventEntity> items = settlementDataMapper.findEvents();
        return items.isEmpty() ? null : items.get(0).getEventNo();
    }

    private void syncSequence(AtomicLong seq, String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        seq.set(parseNumericSuffix(code));
    }

    private long parseNumericSuffix(String code) {
        return Long.parseLong(code.replaceAll("\\D+", ""));
    }

    private String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private String nextNo(String prefix, AtomicLong seq) {
        return prefix + seq.incrementAndGet();
    }
}
