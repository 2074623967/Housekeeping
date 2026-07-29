package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.entity.ClearingBatchEntity;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ClearingRuleEntity;
import com.abc123.clearing.entity.FeeRuleEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import com.abc123.clearing.mapper.ClearingDataMapper;
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
 * 清分系统持久化数据仓，用于第二阶段第一版正式版联调。
 */
@Component
public class ClearingMemoryStore {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicLong batchSeq = new AtomicLong(10000L);
    private final AtomicLong orderSeq = new AtomicLong(20000L);
    private final AtomicLong ruleSeq = new AtomicLong(30000L);
    private final AtomicLong feeSeq = new AtomicLong(40000L);
    private final AtomicLong shareSeq = new AtomicLong(50000L);
    private final AtomicLong eventSeq = new AtomicLong(60000L);
    private final ClearingDataMapper clearingDataMapper;

    public ClearingMemoryStore(ClearingDataMapper clearingDataMapper) {
        this.clearingDataMapper = clearingDataMapper;
    }

    @PostConstruct
    @Transactional
    public void initDemoData() {
        if (clearingDataMapper.countBatches() > 0) {
            syncSequencesFromDatabase();
            return;
        }
        ClearingRuleEntity defaultRule = createRule("家政订单基础清分规则", "ORDER", "平台=8%, 渠道=固定1元, 服务者=余下*90%, 商家=剩余", "否");
        createFeeRule("平台服务费", "PLATFORM_FEE", "RATE", new BigDecimal("0.08"), BigDecimal.ZERO, "用户");
        createFeeRule("渠道手续费", "CHANNEL_FEE", "FIXED", BigDecimal.ZERO, new BigDecimal("1.00"), "平台");
        createBatch("2026-07-20", "MANUAL", "系统管理员", "BATCH-20260720-INIT");
        consumePaymentSuccess(buildDemoEvent(defaultRule.getRuleNo()));
    }

    public List<ClearingBatchEntity> batches() {
        List<ClearingBatchEntity> items = new ArrayList<>(clearingDataMapper.findBatches());
        items.sort(Comparator.comparing(ClearingBatchEntity::getCreatedAt).reversed());
        return items;
    }

    public ClearingBatchEntity findBatch(String batchNo) {
        return clearingDataMapper.findBatch(batchNo);
    }

    @Transactional
    public ClearingBatchEntity createBatch(String batchDate, String sourceType, String createdBy, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            ClearingBatchEntity existingBatch = clearingDataMapper.findBatchByIdempotencyKey(idempotencyKey);
            if (existingBatch != null) {
                return existingBatch;
            }
        }
        ClearingBatchEntity entity = new ClearingBatchEntity();
        entity.setBatchNo(nextNo("CLB", batchSeq));
        entity.setBatchDate(batchDate);
        entity.setSourceType(sourceType);
        entity.setBatchStatus("处理中");
        entity.setTotalOrderCount(0);
        entity.setSuccessOrderCount(0);
        entity.setFailedOrderCount(0);
        entity.setTotalAmount(BigDecimal.ZERO);
        entity.setVersionNo("V1");
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(now());
        entity.setFinishedAt(null);
        entity.setIdempotencyKey(idempotencyKey);
        clearingDataMapper.insertBatch(entity);
        return entity;
    }

    @Transactional
    public ClearingBatchEntity rerunBatch(String batchNo, String operatorName, String reason) {
        ClearingBatchEntity original = findBatch(batchNo);
        ClearingBatchEntity rerun = createBatch(original.getBatchDate(), "RERUN", operatorName, batchNo + "-" + reason);
        rerun.setVersionNo("V" + (parseVersion(original.getVersionNo()) + 1));

        List<ClearingOrderEntity> originalOrders = orders().stream()
                .filter(item -> batchNo.equals(item.getBatchNo()))
                .collect(Collectors.toList());
        for (ClearingOrderEntity order : originalOrders) {
            createOrder(
                    rerun.getBatchNo(),
                    order.getPaymentOrderId() + "-R",
                    order.getOrderNo(),
                    order.getOrderAmount(),
                    order.getRuleNo(),
                    "重跑成功");
        }
        rerun.setBatchStatus("已完成");
        rerun.setFinishedAt(now());
        clearingDataMapper.updateBatch(
                rerun.getBatchNo(),
                rerun.getTotalOrderCount(),
                rerun.getSuccessOrderCount(),
                rerun.getFailedOrderCount(),
                rerun.getTotalAmount(),
                rerun.getVersionNo(),
                rerun.getBatchStatus(),
                rerun.getFinishedAt());
        return findBatch(rerun.getBatchNo());
    }

    public List<ClearingOrderEntity> orders() {
        List<ClearingOrderEntity> items = new ArrayList<>(clearingDataMapper.findOrders());
        items.sort(Comparator.comparing(ClearingOrderEntity::getCreatedAt).reversed());
        return items;
    }

    public ClearingOrderEntity findOrder(String clearingNo) {
        return clearingDataMapper.findOrder(clearingNo);
    }

    public List<ClearingRuleEntity> rules() {
        List<ClearingRuleEntity> items = new ArrayList<>(clearingDataMapper.findRules());
        items.sort(Comparator.comparing(ClearingRuleEntity::getCreatedAt).reversed());
        return items;
    }

    public ClearingRuleEntity findRule(String ruleNo) {
        return clearingDataMapper.findRule(ruleNo);
    }

    @Transactional
    public ClearingRuleEntity createRule(String ruleName, String ruleType, String ruleExpression, String greyFlag) {
        ClearingRuleEntity entity = new ClearingRuleEntity();
        entity.setRuleNo(nextNo("CLR", ruleSeq));
        entity.setRuleName(ruleName);
        entity.setRuleType(ruleType);
        entity.setRuleExpression(ruleExpression);
        entity.setRuleStatus("启用");
        entity.setVersionNo("V1");
        entity.setGreyFlag(greyFlag);
        entity.setCreatedAt(now());
        clearingDataMapper.insertRule(entity);
        return entity;
    }

    @Transactional
    public ClearingRuleEntity updateRuleStatus(String ruleNo, String ruleStatus) {
        clearingDataMapper.updateRuleStatus(ruleNo, ruleStatus);
        return findRule(ruleNo);
    }

    public List<FeeRuleEntity> feeRules() {
        List<FeeRuleEntity> items = new ArrayList<>(clearingDataMapper.findFeeRules());
        items.sort(Comparator.comparing(FeeRuleEntity::getCreatedAt).reversed());
        return items;
    }

    @Transactional
    public FeeRuleEntity createFeeRule(String feeName, String feeType, String feeMode, BigDecimal feeRate, BigDecimal fixedAmount, String feeBearer) {
        FeeRuleEntity entity = new FeeRuleEntity();
        entity.setFeeRuleNo(nextNo("FEE", feeSeq));
        entity.setFeeName(feeName);
        entity.setFeeType(feeType);
        entity.setFeeMode(feeMode);
        entity.setFeeRate(feeRate);
        entity.setFixedAmount(fixedAmount);
        entity.setFeeBearer(feeBearer);
        entity.setStatus("启用");
        entity.setCreatedAt(now());
        clearingDataMapper.insertFeeRule(entity);
        return entity;
    }

    public List<ShareItemEntity> shares() {
        List<ShareItemEntity> items = new ArrayList<>(clearingDataMapper.findShares());
        items.sort(Comparator.comparing(ShareItemEntity::getCreatedAt).reversed());
        return items;
    }

    public List<ShareItemEntity> sharesByClearingNo(String clearingNo) {
        List<ShareItemEntity> items = new ArrayList<>(clearingDataMapper.findSharesByClearingNo(clearingNo));
        items.sort(Comparator.comparing(ShareItemEntity::getCreatedAt).reversed());
        return items;
    }

    public List<ClearingEventEntity> events() {
        List<ClearingEventEntity> items = new ArrayList<>(clearingDataMapper.findEvents());
        items.sort(Comparator.comparing(ClearingEventEntity::getCreatedAt).reversed());
        return items;
    }

    @Transactional
    public ClearingEventEntity consumePaymentSuccess(PaymentSuccessEventRequestDTO request) {
        ClearingRuleEntity activeRule = rules().stream()
                .filter(item -> "启用".equals(item.getRuleStatus()))
                .findFirst()
                .orElseGet(() -> createRule("默认清分规则", "ORDER", "平台=8%, 渠道=固定1元", "否"));

        String batchDate = request.getBatchDate() == null || request.getBatchDate().isEmpty() ? "2026-07-20" : request.getBatchDate();
        ClearingBatchEntity batch = createBatch(batchDate, "EVENT", "系统事件", "EVT-" + request.getPaymentOrderId());
        createOrder(batch.getBatchNo(), request.getPaymentOrderId(), request.getOrderNo(), request.getAmount(), activeRule.getRuleNo(), "清分成功");
        ClearingBatchEntity refreshedBatch = findBatch(batch.getBatchNo());
        refreshedBatch.setBatchStatus("已完成");
        refreshedBatch.setFinishedAt(now());
        clearingDataMapper.updateBatch(
                refreshedBatch.getBatchNo(),
                refreshedBatch.getTotalOrderCount(),
                refreshedBatch.getSuccessOrderCount(),
                refreshedBatch.getFailedOrderCount(),
                refreshedBatch.getTotalAmount(),
                refreshedBatch.getVersionNo(),
                refreshedBatch.getBatchStatus(),
                refreshedBatch.getFinishedAt());

        ClearingEventEntity event = new ClearingEventEntity();
        event.setEventNo(nextNo("EVT", eventSeq));
        event.setEventType("PAYMENT_SUCCESS");
        event.setBizNo(request.getPaymentOrderId());
        event.setSummary(request.getCustomerName() + " 支付成功后触发清分");
        event.setPayload("{\"paymentOrderId\":\"" + request.getPaymentOrderId() + "\",\"orderNo\":\"" + request.getOrderNo() + "\"}");
        event.setEventStatus("已消费");
        event.setCreatedAt(now());
        clearingDataMapper.insertEvent(event);
        return event;
    }

    @Transactional
    protected ClearingOrderEntity createOrder(String batchNo, String paymentOrderId, String orderNo, BigDecimal orderAmount,
            String ruleNo, String successStatus) {
        BigDecimal platformAmount = orderAmount.multiply(new BigDecimal("0.08")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal channelFeeAmount = new BigDecimal("1.00");
        BigDecimal merchantAmount = orderAmount.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal workerAmount = orderAmount.subtract(platformAmount).subtract(channelFeeAmount).subtract(merchantAmount);

        ClearingOrderEntity entity = new ClearingOrderEntity();
        entity.setClearingNo(nextNo("CLO", orderSeq));
        entity.setBatchNo(batchNo);
        entity.setPaymentOrderId(paymentOrderId);
        entity.setOrderNo(orderNo);
        entity.setOrderAmount(orderAmount);
        entity.setMerchantAmount(merchantAmount);
        entity.setWorkerAmount(workerAmount);
        entity.setPlatformAmount(platformAmount);
        entity.setChannelFeeAmount(channelFeeAmount);
        entity.setSubsidyAmount(BigDecimal.ZERO);
        entity.setClearingStatus(successStatus);
        entity.setRuleNo(ruleNo);
        entity.setCreatedAt(now());
        clearingDataMapper.insertOrder(entity);

        createShare(entity.getClearingNo(), "WORKER", "WRK1001", "李阿姨", workerAmount);
        createShare(entity.getClearingNo(), "MERCHANT", "MCH1001", "上海静安门店", merchantAmount);
        createShare(entity.getClearingNo(), "PLATFORM", "PLT1001", "家政平台", platformAmount);

        ClearingBatchEntity batch = findBatch(batchNo);
        batch.setTotalOrderCount(batch.getTotalOrderCount() + 1);
        batch.setSuccessOrderCount(batch.getSuccessOrderCount() + 1);
        batch.setTotalAmount(batch.getTotalAmount().add(orderAmount));
        clearingDataMapper.updateBatch(
                batch.getBatchNo(),
                batch.getTotalOrderCount(),
                batch.getSuccessOrderCount(),
                batch.getFailedOrderCount(),
                batch.getTotalAmount(),
                batch.getVersionNo(),
                batch.getBatchStatus(),
                batch.getFinishedAt());
        return entity;
    }

    @Transactional
    protected ShareItemEntity createShare(String clearingNo, String shareType, String shareTargetNo, String shareTargetName,
            BigDecimal shareAmount) {
        ShareItemEntity entity = new ShareItemEntity();
        entity.setShareItemNo(nextNo("SHR", shareSeq));
        entity.setClearingNo(clearingNo);
        entity.setShareType(shareType);
        entity.setShareTargetNo(shareTargetNo);
        entity.setShareTargetName(shareTargetName);
        entity.setShareAmount(shareAmount);
        entity.setShareStatus("待结算");
        entity.setCreatedAt(now());
        clearingDataMapper.insertShare(entity);
        return entity;
    }

    private PaymentSuccessEventRequestDTO buildDemoEvent(String ruleNo) {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200001");
        request.setOrderNo("ORD202607200001");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("张女士");
        request.setMerchantName("上海静安门店");
        request.setWorkerName("李阿姨");
        request.setAmount(new BigDecimal("168.00"));
        return request;
    }

    private void syncSequencesFromDatabase() {
        syncSequence(batchSeq, firstBatchNo());
        syncSequence(orderSeq, firstOrderNo());
        syncSequence(ruleSeq, firstRuleNo());
        syncSequence(feeSeq, firstFeeRuleNo());
        syncSequence(shareSeq, firstShareItemNo());
        syncSequence(eventSeq, firstEventNo());
    }

    private String firstBatchNo() {
        List<ClearingBatchEntity> items = clearingDataMapper.findBatches();
        return items.isEmpty() ? null : items.get(0).getBatchNo();
    }

    private String firstOrderNo() {
        List<ClearingOrderEntity> items = clearingDataMapper.findOrders();
        return items.isEmpty() ? null : items.get(0).getClearingNo();
    }

    private String firstRuleNo() {
        List<ClearingRuleEntity> items = clearingDataMapper.findRules();
        return items.isEmpty() ? null : items.get(0).getRuleNo();
    }

    private String firstFeeRuleNo() {
        List<FeeRuleEntity> items = clearingDataMapper.findFeeRules();
        return items.isEmpty() ? null : items.get(0).getFeeRuleNo();
    }

    private String firstShareItemNo() {
        List<ShareItemEntity> items = clearingDataMapper.findShares();
        return items.isEmpty() ? null : items.get(0).getShareItemNo();
    }

    private String firstEventNo() {
        List<ClearingEventEntity> items = clearingDataMapper.findEvents();
        return items.isEmpty() ? null : items.get(0).getEventNo();
    }

    private void syncSequence(AtomicLong seq, String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        seq.set(parseNumericSuffix(code));
    }

    private int parseVersion(String versionNo) {
        return Integer.parseInt(versionNo.replace("V", ""));
    }

    private long parseNumericSuffix(String code) {
        return Long.parseLong(code.replaceAll("\\D+", ""));
    }

    private String nextNo(String prefix, AtomicLong seq) {
        return prefix + seq.incrementAndGet();
    }

    private String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }
}
