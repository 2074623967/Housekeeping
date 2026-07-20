package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.entity.PayoutBatchEntity;
import com.abc123.settlement.entity.PayoutRecordEntity;
import com.abc123.settlement.entity.SettlementAuditLogEntity;
import com.abc123.settlement.entity.SettlementBatchEntity;
import com.abc123.settlement.entity.SettlementEventEntity;
import com.abc123.settlement.entity.SettlementItemEntity;
import com.abc123.settlement.entity.SettlementOrderEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * 结算系统内存态数据仓，用于第一版骨架联调。
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

    private final Map<String, SettlementBatchEntity> batches = new LinkedHashMap<>();
    private final Map<String, SettlementOrderEntity> orders = new LinkedHashMap<>();
    private final Map<String, List<SettlementItemEntity>> items = new LinkedHashMap<>();
    private final Map<String, List<SettlementAuditLogEntity>> auditLogs = new LinkedHashMap<>();
    private final Map<String, PayoutBatchEntity> payoutBatches = new LinkedHashMap<>();
    private final Map<String, List<PayoutRecordEntity>> payoutRecords = new LinkedHashMap<>();
    private final List<SettlementEventEntity> events = new ArrayList<>();

    @PostConstruct
    public void initDemoData() {
        SettlementBatchEntity batch = createBatch("2026-07-20", "MANUAL", "结算运营", "SETTLE-20260720-INIT");
        SettlementOrderEntity workerOrder = createOrder(batch.getBatchNo(), "WORKER", "WRK1001", "李阿姨", new BigDecimal("120.00"), new BigDecimal("8.00"), "待审核", "CLO20001");
        SettlementOrderEntity merchantOrder = createOrder(batch.getBatchNo(), "MERCHANT", "MCH1001", "上海静安门店", new BigDecimal("20.00"), new BigDecimal("0.00"), "待审核", "CLO20001");
        createItem(workerOrder.getSettlementNo(), "基础结算", "应结金额", new BigDecimal("120.00"));
        createItem(workerOrder.getSettlementNo(), "结算扣减", "扣减金额", new BigDecimal("8.00"));
        createItem(merchantOrder.getSettlementNo(), "商家结算", "应结金额", new BigDecimal("20.00"));
        createAuditLog(workerOrder.getSettlementNo(), "创建结算单", "待审核", "系统");
        createAuditLog(merchantOrder.getSettlementNo(), "创建结算单", "待审核", "系统");
        batch.setBatchStatus("待审核");
        batch.setTotalCount(2);
        batch.setTotalAmount(new BigDecimal("140.00"));
        consumeClearingGenerated(buildDemoEvent());
    }

    public List<SettlementBatchEntity> batches() {
        return batches.values().stream().sorted(Comparator.comparing(SettlementBatchEntity::getCreatedAt).reversed()).collect(Collectors.toList());
    }

    public SettlementBatchEntity findBatch(String batchNo) {
        return batches.get(batchNo);
    }

    public SettlementBatchEntity createBatch(String batchDate, String settlementType, String createdBy, String idempotencyKey) {
        for (SettlementBatchEntity batch : batches.values()) {
            if (idempotencyKey != null && idempotencyKey.equals(batch.getIdempotencyKey())) {
                return batch;
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
        batches.put(entity.getBatchNo(), entity);
        return entity;
    }

    public List<SettlementOrderEntity> orders() {
        return orders.values().stream().sorted(Comparator.comparing(SettlementOrderEntity::getCreatedAt).reversed()).collect(Collectors.toList());
    }

    public SettlementOrderEntity findOrder(String settlementNo) {
        return orders.get(settlementNo);
    }

    public SettlementOrderEntity createOrder(String batchNo, String targetType, String targetNo, String targetName, BigDecimal shouldSettleAmount, BigDecimal deductAmount, String settlementStatus, String clearingNo) {
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
        orders.put(entity.getSettlementNo(), entity);
        batches.get(batchNo).setTotalCount(batches.get(batchNo).getTotalCount() + 1);
        batches.get(batchNo).setTotalAmount(batches.get(batchNo).getTotalAmount().add(entity.getNetSettleAmount()));
        return entity;
    }

    public List<SettlementItemEntity> itemsBySettlementNo(String settlementNo) {
        return items.getOrDefault(settlementNo, new ArrayList<>());
    }

    public SettlementItemEntity createItem(String settlementNo, String itemType, String itemName, BigDecimal itemAmount) {
        SettlementItemEntity entity = new SettlementItemEntity();
        entity.setItemNo(nextNo("ITM", itemSeq));
        entity.setSettlementNo(settlementNo);
        entity.setItemType(itemType);
        entity.setItemName(itemName);
        entity.setItemAmount(itemAmount);
        entity.setCreatedAt(now());
        items.computeIfAbsent(settlementNo, key -> new ArrayList<>()).add(entity);
        return entity;
    }

    public List<SettlementAuditLogEntity> auditLogsBySettlementNo(String settlementNo) {
        return auditLogs.getOrDefault(settlementNo, new ArrayList<>());
    }

    public SettlementAuditLogEntity createAuditLog(String settlementNo, String action, String result, String operatorName) {
        return createAuditLog(settlementNo, action, result, operatorName, "");
    }

    public SettlementAuditLogEntity createAuditLog(String settlementNo, String action, String result, String operatorName, String remark) {
        SettlementAuditLogEntity entity = new SettlementAuditLogEntity();
        entity.setAuditNo(nextNo("AUD", auditSeq));
        entity.setSettlementNo(settlementNo);
        entity.setAuditAction(action);
        entity.setAuditResult(result);
        entity.setOperatorName(operatorName);
        entity.setRemark(remark);
        entity.setCreatedAt(now());
        auditLogs.computeIfAbsent(settlementNo, key -> new ArrayList<>()).add(entity);
        return entity;
    }

    public List<PayoutBatchEntity> payoutBatches() {
        return payoutBatches.values().stream().sorted(Comparator.comparing(PayoutBatchEntity::getCreatedAt).reversed()).collect(Collectors.toList());
    }

    public PayoutBatchEntity findPayoutBatch(String payoutBatchNo) {
        return payoutBatches.get(payoutBatchNo);
    }

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
        payoutBatches.put(entity.getPayoutBatchNo(), entity);
        return entity;
    }

    public List<PayoutRecordEntity> payoutRecordsByBatchNo(String payoutBatchNo) {
        return payoutRecords.getOrDefault(payoutBatchNo, new ArrayList<>());
    }

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
        payoutRecords.computeIfAbsent(payoutBatchNo, key -> new ArrayList<>()).add(entity);
        PayoutBatchEntity batch = payoutBatches.get(payoutBatchNo);
        batch.setPayoutCount(batch.getPayoutCount() + 1);
        batch.setSuccessCount(batch.getSuccessCount() + 1);
        batch.setTotalAmount(batch.getTotalAmount().add(order.getNetSettleAmount()));
        return entity;
    }

    public List<SettlementEventEntity> events() {
        return events.stream().sorted(Comparator.comparing(SettlementEventEntity::getCreatedAt).reversed()).collect(Collectors.toList());
    }

    public SettlementEventEntity consumeClearingGenerated(ClearingGeneratedEventRequestDTO request) {
        SettlementBatchEntity batch = createBatch("2026-07-20", "EVENT", "系统事件", "EVENT-" + request.getClearingNo());
        SettlementOrderEntity order = createOrder(batch.getBatchNo(), request.getTargetType(), request.getTargetNo(), request.getTargetName(), request.getShouldSettleAmount(), request.getDeductAmount(), "待审核", request.getClearingNo());
        createItem(order.getSettlementNo(), "应结金额", "应结", request.getShouldSettleAmount());
        createItem(order.getSettlementNo(), "扣减金额", "扣减", request.getDeductAmount());

        SettlementEventEntity entity = new SettlementEventEntity();
        entity.setEventNo(nextNo("SVE", eventSeq));
        entity.setEventType("CLEARING_GENERATED");
        entity.setBizNo(request.getClearingNo());
        entity.setSummary("清分结果生成后自动生成结算单");
        entity.setPayload("{\"clearingNo\":\"" + request.getClearingNo() + "\"}");
        entity.setEventStatus("已消费");
        entity.setCreatedAt(now());
        events.add(entity);
        return entity;
    }

    public SettlementOrderEntity audit(String settlementNo, String operatorName, String remark, boolean approved) {
        SettlementOrderEntity entity = findOrder(settlementNo);
        entity.setAuditStatus(approved ? "已通过" : "已退回");
        entity.setSettlementStatus(approved ? "待出款" : "已退回");
        createAuditLog(settlementNo, approved ? "审核通过" : "审核退回", approved ? "通过" : "退回", operatorName, remark);
        SettlementBatchEntity batch = batches.get(entity.getBatchNo());
        if (approved) {
            batch.setAuditedCount(batch.getAuditedCount() + 1);
            batch.setBatchStatus("待出款");
        } else {
            batch.setBatchStatus("存在退回");
        }
        return entity;
    }

    public PayoutBatchEntity retryPayoutBatch(String payoutBatchNo, String operatorName, String reason) {
        PayoutBatchEntity entity = findPayoutBatch(payoutBatchNo);
        entity.setPayoutStatus("重试中");
        entity.setFinishedAt(now());
        for (PayoutRecordEntity record : payoutRecordsByBatchNo(payoutBatchNo)) {
            if (!"已发放".equals(record.getPayoutStatus())) {
                record.setRetryCount(record.getRetryCount() + 1);
                record.setPayoutStatus("已发放");
            }
        }
        entity.setPayoutStatus("已完成");
        return entity;
    }

    private String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private String nextNo(String prefix, AtomicLong seq) {
        return prefix + seq.incrementAndGet();
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
}
