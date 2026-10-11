package com.abc123.reconciliation.service.impl;

import com.abc123.reconciliation.common.BusinessException;
import com.abc123.reconciliation.dao.ReconciliationDao;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.BatchQueryDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchDetailDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.dto.CrossSystemChainSnapshotDTO;
import com.abc123.reconciliation.dto.CrossSystemFactDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import com.abc123.reconciliation.service.ReconciliationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 对账业务编排。
 *
 * <p>V1 按支付单号建立匹配键，并严格比较金额和成功状态。</p>
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final String CREATED = "CREATED";
    private static final String RUNNING = "RUNNING";
    private static final String COMPLETED = "COMPLETED";
    private static final String SUCCESS = "SUCCESS";
    private static final String READINESS_NOT_CHECKED = "NOT_CHECKED";
    private static final String READINESS_BLOCKED = "BLOCKED";
    private static final String READINESS_CONDITIONAL = "CONDITIONAL";
    private static final String READINESS_READY = "READY";
    private static final String CHAIN_ID_PREFIX = "CHAIN-";
    private static final String CHAIN_WAITING = "WAITING";
    private static final String CHAIN_IN_PROGRESS = "IN_PROGRESS";
    private static final String CHAIN_EXCEPTION = "EXCEPTION";
    private static final String CHAIN_COMPLETED = "COMPLETED";
    private static final String RESPONSIBILITY_UNASSIGNED = "UNASSIGNED";
    private static final String RESPONSIBILITY_PENDING = "PENDING";
    private static final String RESPONSIBILITY_MANUAL_REVIEW = "MANUAL_REVIEW";
    private static final String RESPONSIBILITY_CLOSED = "CLOSED";
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
    private static final String PAYMENT_STAGE = "payment-core|PAYMENT_SUCCESS";
    private static final String CLEARING_STAGE = "clearing-system|CLEARING_GENERATED";
    private static final String SETTLEMENT_STAGE = "settlement-system|SETTLEMENT_CREATED";
    private static final String ACCOUNTING_STAGE = "accounting-system|ACCOUNTING_POSTED";
    private static final List<String> EXPECTED_STAGES = java.util.Arrays.asList(
            PAYMENT_STAGE, CLEARING_STAGE, SETTLEMENT_STAGE, ACCOUNTING_STAGE);
    private static final DateTimeFormatter BATCH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReconciliationDao dao;
    private final TransactionTemplate transactionTemplate;

    public ReconciliationServiceImpl(ReconciliationDao dao, PlatformTransactionManager transactionManager) {
        this.dao = dao;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public ReconciliationBatchListItemDTO createBatch(ReconciliationBatchCreateRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getBusinessDate())
                || !StringUtils.hasText(request.getChannelCode())) {
            throw new BusinessException("业务日期和渠道编码不能为空");
        }
        LocalDate.parse(request.getBusinessDate().trim());
        String batchNo = "REC" + LocalDateTime.now().format(BATCH_FORMATTER)
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        dao.insertBatch(batchNo, request.getBusinessDate().trim(), request.getChannelCode().trim(),
                StringUtils.hasText(request.getBillSource()) ? request.getBillSource().trim() : "CHANNEL_FILE");
        return requiredBatch(batchNo);
    }

    @Override
    @Transactional
    public ReconciliationBatchListItemDTO addChannelRecord(String batchNo, ChannelRecordRequestDTO request) {
        requiredBatch(batchNo);
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())) {
            throw new BusinessException("渠道账单记录字段不完整");
        }
        validateRecordAmount(request.getAmount(), "渠道账单金额必须大于0且最多保留2位小数");
        ChannelRecordEntity entity = new ChannelRecordEntity();
        entity.setBatchNo(batchNo);
        entity.setChannelTradeNo(required(request.getChannelTradeNo(), "渠道流水号不能为空"));
        entity.setPaymentOrderId(request.getPaymentOrderId().trim());
        entity.setAmount(request.getAmount());
        entity.setTradeStatus(StringUtils.hasText(request.getTradeStatus()) ? request.getTradeStatus().trim() : SUCCESS);
        entity.setTradeTime(request.getTradeTime() == null ? LocalDateTime.now() : request.getTradeTime());
        dao.insertChannelRecord(batchNo, entity);
        return requiredBatch(batchNo);
    }

    @Override
    @Transactional
    public ReconciliationBatchListItemDTO addInternalRecord(String batchNo, InternalRecordRequestDTO request) {
        requiredBatch(batchNo);
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())) {
            throw new BusinessException("平台支付事实字段不完整");
        }
        validateRecordAmount(request.getAmount(), "平台支付事实金额必须大于0且最多保留2位小数");
        InternalRecordEntity entity = new InternalRecordEntity();
        entity.setBatchNo(batchNo);
        entity.setPaymentOrderId(request.getPaymentOrderId().trim());
        entity.setAmount(request.getAmount());
        entity.setInternalStatus(StringUtils.hasText(request.getInternalStatus()) ? request.getInternalStatus().trim() : SUCCESS);
        entity.setSourceSystem(StringUtils.hasText(request.getSourceSystem()) ? request.getSourceSystem().trim() : "payment-core");
        entity.setPaidTime(request.getPaidTime() == null ? LocalDateTime.now() : request.getPaidTime());
        dao.insertInternalRecord(batchNo, entity);
        return requiredBatch(batchNo);
    }

    private void validateRecordAmount(BigDecimal amount, String message) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw new BusinessException(message);
        }
    }

    @Override
    public ReconciliationBatchListItemDTO run(String batchNo) {
        requiredBatch(batchNo);
        ReconciliationBatchListItemDTO readiness = checkBatchReadiness(batchNo);
        if (!READINESS_READY.equals(readiness.getReadinessStatus())) {
            throw new BusinessException("对账准入未通过：" + readiness.getReadinessSummary());
        }
        if (dao.claimBatchRun(batchNo) == 0) {
            throw new BusinessException("对账批次正在执行");
        }
        return transactionTemplate.execute(status -> executeRun(batchNo));
    }

    @Override
    @Transactional
    public ReconciliationBatchListItemDTO checkBatchReadiness(String batchNo) {
        ReconciliationBatchListItemDTO batch = requiredBatch(batchNo);
        List<InternalRecordEntity> internalRecords = dao.findInternalRecords(batch.getBatchNo());
        BatchReadinessEvaluation evaluation = evaluateBatchReadiness(internalRecords);
        if (dao.updateBatchReadiness(batch.getBatchNo(), evaluation.readinessStatus,
                evaluation.readinessSummary, evaluation.readinessSuggestedAction,
                evaluation.readinessBlockingOwner, evaluation.chainCount,
                evaluation.completedChainCount, evaluation.abnormalChainCount,
                evaluation.pendingChainCount) == 0) {
            throw new BusinessException("对账批次不存在");
        }
        return requiredBatch(batch.getBatchNo());
    }

    @Override
    public ReconciliationBatchDetailDTO detail(String batchNo) {
        ReconciliationBatchListItemDTO batch = requiredBatch(batchNo);
        ReconciliationBatchDetailDTO detail = new ReconciliationBatchDetailDTO();
        detail.setBatchNo(batch.getBatchNo());
        detail.setBusinessDate(batch.getBusinessDate());
        detail.setChannelCode(batch.getChannelCode());
        detail.setBillSource(batch.getBillSource());
        detail.setStatus(batch.getStatus());
        detail.setChannelCount(batch.getChannelCount());
        detail.setInternalCount(batch.getInternalCount());
        detail.setMatchedCount(batch.getMatchedCount());
        detail.setDifferenceCount(batch.getDifferenceCount());
        detail.setReadinessStatus(batch.getReadinessStatus());
        detail.setReadinessSummary(batch.getReadinessSummary());
        detail.setReadinessSuggestedAction(batch.getReadinessSuggestedAction());
        detail.setReadinessBlockingOwner(batch.getReadinessBlockingOwner());
        detail.setReadinessChainCount(batch.getReadinessChainCount());
        detail.setReadinessCompletedChainCount(batch.getReadinessCompletedChainCount());
        detail.setReadinessAbnormalChainCount(batch.getReadinessAbnormalChainCount());
        detail.setReadinessPendingChainCount(batch.getReadinessPendingChainCount());
        detail.setReadinessCheckedAt(batch.getReadinessCheckedAt());
        detail.setOpenDifferenceCount((int) dao.countDifferencesByBatchAndStatus(batch.getBatchNo(), "OPEN"));
        detail.setResolvedDifferenceCount((int) dao.countDifferencesByBatchAndStatus(batch.getBatchNo(), "RESOLVED"));
        detail.setChannelRecords(dao.findChannelRecordDetails(batch.getBatchNo()));
        detail.setInternalRecords(dao.findInternalRecordDetails(batch.getBatchNo()));
        detail.setDifferences(dao.findDifferencesByBatch(batch.getBatchNo()));
        return detail;
    }

    @Override
    public byte[] exportBatchDetail(String batchNo) {
        ReconciliationBatchDetailDTO detail = detail(batchNo);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("批次号,业务日期,渠道编码,账单来源,批次状态,对账准入状态,对账准入结论,准入链路数,已完成链路数,异常链路数,待收口链路数,渠道记录数,平台记录数,已匹配数,差异数,未结案差异数,已结案差异数\n");
        builder.append(csv(detail.getBatchNo())).append(',')
                .append(csv(detail.getBusinessDate() == null ? null : detail.getBusinessDate().toString())).append(',')
                .append(csv(detail.getChannelCode())).append(',')
                .append(csv(detail.getBillSource())).append(',')
                .append(csv(detail.getStatus())).append(',')
                .append(csv(detail.getReadinessStatus())).append(',')
                .append(csv(detail.getReadinessSummary())).append(',')
                .append(csv(String.valueOf(detail.getReadinessChainCount()))).append(',')
                .append(csv(String.valueOf(detail.getReadinessCompletedChainCount()))).append(',')
                .append(csv(String.valueOf(detail.getReadinessAbnormalChainCount()))).append(',')
                .append(csv(String.valueOf(detail.getReadinessPendingChainCount()))).append(',')
                .append(csv(String.valueOf(detail.getChannelCount()))).append(',')
                .append(csv(String.valueOf(detail.getInternalCount()))).append(',')
                .append(csv(String.valueOf(detail.getMatchedCount()))).append(',')
                .append(csv(String.valueOf(detail.getDifferenceCount()))).append(',')
                .append(csv(String.valueOf(detail.getOpenDifferenceCount()))).append(',')
                .append(csv(String.valueOf(detail.getResolvedDifferenceCount()))).append('\n');
        builder.append('\n');
        builder.append("渠道账单记录\n");
        builder.append("渠道流水号,支付单号,金额,状态,交易时间\n");
        for (int i = 0; i < detail.getChannelRecords().size(); i++) {
            com.abc123.reconciliation.dto.ReconciliationBatchChannelRecordDTO item = detail.getChannelRecords().get(i);
            builder.append(csv(item.getChannelTradeNo())).append(',')
                    .append(csv(item.getPaymentOrderId())).append(',')
                    .append(csv(item.getAmount() == null ? null : item.getAmount().toPlainString())).append(',')
                    .append(csv(item.getTradeStatus())).append(',')
                    .append(csv(item.getTradeTime() == null ? null : item.getTradeTime().toString()))
                    .append('\n');
        }
        builder.append('\n');
        builder.append("平台支付事实\n");
        builder.append("支付单号,金额,状态,来源系统,支付时间\n");
        for (int i = 0; i < detail.getInternalRecords().size(); i++) {
            com.abc123.reconciliation.dto.ReconciliationBatchInternalRecordDTO item = detail.getInternalRecords().get(i);
            builder.append(csv(item.getPaymentOrderId())).append(',')
                    .append(csv(item.getAmount() == null ? null : item.getAmount().toPlainString())).append(',')
                    .append(csv(item.getInternalStatus())).append(',')
                    .append(csv(item.getSourceSystem())).append(',')
                    .append(csv(item.getPaidTime() == null ? null : item.getPaidTime().toString()))
                    .append('\n');
        }
        builder.append('\n');
        builder.append("差异明细\n");
        builder.append("差异编号,支付单号,差异类型,渠道金额,平台金额,状态,处置结论,处置备注,发现时间,结案时间\n");
        for (int i = 0; i < detail.getDifferences().size(); i++) {
            ReconciliationDifferenceDTO item = detail.getDifferences().get(i);
            builder.append(csv(item.getDifferenceNo())).append(',')
                    .append(csv(item.getPaymentOrderId())).append(',')
                    .append(csv(item.getDifferenceType())).append(',')
                    .append(csv(item.getChannelAmount() == null ? null : item.getChannelAmount().toPlainString())).append(',')
                    .append(csv(item.getInternalAmount() == null ? null : item.getInternalAmount().toPlainString())).append(',')
                    .append(csv(item.getStatus())).append(',')
                    .append(csv(item.getResolution())).append(',')
                    .append(csv(item.getRemark())).append(',')
                    .append(csv(item.getDetectedAt() == null ? null : item.getDetectedAt().toString())).append(',')
                    .append(csv(item.getResolvedAt() == null ? null : item.getResolvedAt().toString()))
                    .append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public PageResultDTO<ReconciliationBatchListItemDTO> batches(BatchQueryDTO query) {
        if (query == null) {
            query = new BatchQueryDTO();
        }
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(dao.findBatches(query), dao.countBatches(),
                query.getPageNo(), query.getPageSize());
    }

    @Override
    public PageResultDTO<ReconciliationDifferenceDTO> differences(DifferenceQueryDTO query) {
        if (query == null) {
            query = new DifferenceQueryDTO();
        }
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(dao.findDifferences(query), dao.countDifferences(query),
                query.getPageNo(), query.getPageSize());
    }

    @Override
    @Transactional
    public void resolve(DifferenceResolveRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getDifferenceNo())
                || !StringUtils.hasText(request.getResolution())) {
            throw new BusinessException("差异编号和处置结论不能为空");
        }
        if (dao.resolveDifference(request.getDifferenceNo().trim(), request.getResolution().trim(),
                StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null) == 0) {
            throw new BusinessException("差异不存在或已结案");
        }
    }

    @Override
    public ReconciliationOverviewDTO overview() {
        return dao.overview();
    }

    @Override
    @Transactional
    public CrossSystemChainSnapshotDTO recordCrossSystemFact(CrossSystemFactRequestDTO request) {
        validateCrossSystemFact(request);
        validateRecordAmount(request.getAmount(), "跨系统事实金额必须大于0且最多保留2位小数");
        request.setBusinessChainId(request.getBusinessChainId().trim());
        request.setSystemCode(request.getSystemCode().trim());
        request.setFactType(request.getFactType().trim());
        request.setFactNo(request.getFactNo().trim());
        request.setBusinessNo(required(request.getBusinessNo(), "来源业务编号不能为空"));
        request.setFactStatus(required(request.getFactStatus(), "事实状态不能为空"));
        if (request.getOccurredAt() == null) {
            request.setOccurredAt(LocalDateTime.now());
        }
        CrossSystemFactDTO existing = dao.findCrossSystemFact(
                request.getBusinessChainId(), request.getSystemCode(), request.getFactType(), request.getFactNo());
        if (existing != null) {
            validateRepeatedCrossSystemFact(existing, request);
        }
        dao.insertCrossSystemFact(request);
        return crossSystemChain(request.getBusinessChainId());
    }

    private void validateRepeatedCrossSystemFact(CrossSystemFactDTO existing, CrossSystemFactRequestDTO request) {
        if (!Objects.equals(existing.getBusinessNo(), request.getBusinessNo())
                || (!Objects.equals(existing.getFactStatus(), request.getFactStatus())
                    && !("PENDING".equals(existing.getFactStatus())
                        && "SUCCESS".equals(request.getFactStatus())))
                || !Objects.equals(existing.getPayScene(), request.getPayScene())
                || existing.getAmount() == null
                || request.getAmount() == null
                || existing.getAmount().compareTo(request.getAmount()) != 0) {
            throw new BusinessException("重复跨系统事实与已落库事实不一致");
        }
    }

    @Override
    public CrossSystemChainSnapshotDTO crossSystemChain(String businessChainId) {
        if (!StringUtils.hasText(businessChainId)) {
            throw new BusinessException("业务链路 ID 不能为空");
        }
        List<CrossSystemFactDTO> facts = dao.findCrossSystemFacts(businessChainId.trim());
        CrossSystemChainSnapshotDTO snapshot = new CrossSystemChainSnapshotDTO();
        snapshot.setBusinessChainId(businessChainId.trim());
        snapshot.setFactCount(facts.size());
        snapshot.setFacts(facts);
        ChainEvaluation evaluation = evaluateChain(facts);
        snapshot.setHasAbnormalFact(evaluation.abnormalFactCount > 0);
        snapshot.setChainStatus(evaluation.chainStatus);
        snapshot.setResponsibilityStatus(evaluation.responsibilityStatus);
        snapshot.setNextAction(evaluation.nextAction);
        snapshot.setAbnormalFactCount(evaluation.abnormalFactCount);
        snapshot.setCompletedStageCount(evaluation.completedStageCount);
        snapshot.setExpectedStageCount(EXPECTED_STAGES.size());
        snapshot.setFinancialCloseable(evaluation.financialCloseable);
        snapshot.setLastFactAt(evaluation.lastFactAt);
        snapshot.setDiagnosis(evaluation.diagnosis);
        return snapshot;
    }

    /**
     * 根据事实快照计算统一链路状态，避免各系统分别解释“处理中/失败/已收口”。
     */
    private ChainEvaluation evaluateChain(List<CrossSystemFactDTO> facts) {
        if (facts == null || facts.isEmpty()) {
            return new ChainEvaluation(
                    CHAIN_WAITING,
                    RESPONSIBILITY_UNASSIGNED,
                    "等待 payment-core 上报 PAYMENT_SUCCESS",
                    0,
                    0,
                    false,
                    null,
                    "未接收到任何跨系统事实");
        }
        int abnormalFactCount = (int) facts.stream()
                .filter(item -> isAbnormalStatus(item.getFactStatus()))
                .count();
        LocalDateTime lastFactAt = facts.stream()
                .map(CrossSystemFactDTO::getOccurredAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        if (abnormalFactCount > 0) {
            return new ChainEvaluation(
                    CHAIN_EXCEPTION,
                    RESPONSIBILITY_MANUAL_REVIEW,
                    "创建或重试补偿任务，完成异常事实人工复核",
                    abnormalFactCount,
                    countCompletedStages(facts),
                    false,
                    lastFactAt,
                    "链路存在失败或异常事实，禁止直接进入财务收口");
        }
        int completedStageCount = countCompletedStages(facts);
        if (completedStageCount == EXPECTED_STAGES.size()) {
            return new ChainEvaluation(
                    CHAIN_COMPLETED,
                    RESPONSIBILITY_CLOSED,
                    "允许进入财务收口并归档链路事实",
                    0,
                    completedStageCount,
                    true,
                    lastFactAt,
                    "支付、清分、结算、账务关键事实已完整收口");
        }
        return new ChainEvaluation(
                CHAIN_IN_PROGRESS,
                RESPONSIBILITY_PENDING,
                buildMissingStageAction(facts),
                0,
                completedStageCount,
                false,
                lastFactAt,
                "已接收部分跨系统事实，仍有关键阶段未收口");
    }

    private int countCompletedStages(List<CrossSystemFactDTO> facts) {
        Set<String> completedStages = facts.stream()
                .filter(item -> isSuccessfulStatus(item.getFactStatus()))
                .map(item -> item.getSystemCode() + "|" + item.getFactType())
                .collect(Collectors.toSet());
        return (int) EXPECTED_STAGES.stream().filter(completedStages::contains).count();
    }

    /**
     * 按批次内的平台支付单聚合统一业务链路，形成正式对账前的财务准入快照。
     */
    private BatchReadinessEvaluation evaluateBatchReadiness(List<InternalRecordEntity> internalRecords) {
        if (internalRecords == null || internalRecords.isEmpty()) {
            return new BatchReadinessEvaluation(
                    READINESS_BLOCKED,
                    "平台支付事实为空，禁止进入正式对账",
                    "等待 payment-core 导入或上报支付成功事实",
                    "payment-core",
                    0,
                    0,
                    0,
                    0);
        }
        Set<String> paymentOrderIds = internalRecords.stream()
                .map(InternalRecordEntity::getPaymentOrderId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        if (paymentOrderIds.isEmpty()) {
            return new BatchReadinessEvaluation(
                    READINESS_BLOCKED,
                    "平台支付事实缺少有效支付单号，禁止进入正式对账",
                    "补齐平台支付单号后重新检查准入",
                    "reconciliation-ops",
                    0,
                    0,
                    0,
                    0);
        }
        int completedChainCount = 0;
        int abnormalChainCount = 0;
        int pendingChainCount = 0;
        String firstNextAction = null;
        for (String paymentOrderId : paymentOrderIds) {
            CrossSystemChainSnapshotDTO snapshot = crossSystemChain(CHAIN_ID_PREFIX + paymentOrderId);
            if (CHAIN_COMPLETED.equals(snapshot.getChainStatus())) {
                completedChainCount++;
            } else if (CHAIN_EXCEPTION.equals(snapshot.getChainStatus())) {
                abnormalChainCount++;
            } else {
                pendingChainCount++;
                if (!StringUtils.hasText(firstNextAction)) {
                    firstNextAction = snapshot.getNextAction();
                }
            }
        }
        int chainCount = paymentOrderIds.size();
        if (abnormalChainCount > 0) {
            return new BatchReadinessEvaluation(
                    READINESS_BLOCKED,
                    "存在异常跨系统业务链路，禁止进入正式对账",
                    "先处理异常链路并完成补偿或人工复核",
                    "reconciliation-ops",
                    chainCount,
                    completedChainCount,
                    abnormalChainCount,
                    pendingChainCount);
        }
        if (pendingChainCount > 0) {
            return new BatchReadinessEvaluation(
                    READINESS_CONDITIONAL,
                    "部分跨系统业务链路尚未完整收口，暂不执行正式对账",
                    StringUtils.hasText(firstNextAction) ? firstNextAction : "等待下游关键事实收口",
                    "payment-clearing-settlement-accounting",
                    chainCount,
                    completedChainCount,
                    abnormalChainCount,
                    pendingChainCount);
        }
        return new BatchReadinessEvaluation(
                READINESS_READY,
                "批次内跨系统业务链路已完整收口，可进入正式对账",
                "允许执行自动对账并继续核对渠道账单与平台支付事实",
                null,
                chainCount,
                completedChainCount,
                abnormalChainCount,
                pendingChainCount);
    }

    private String buildMissingStageAction(List<CrossSystemFactDTO> facts) {
        Set<String> completedStages = facts.stream()
                .filter(item -> isSuccessfulStatus(item.getFactStatus()))
                .map(item -> item.getSystemCode() + "|" + item.getFactType())
                .collect(Collectors.toSet());
        String nextStage = EXPECTED_STAGES.stream()
                .filter(stage -> !completedStages.contains(stage))
                .findFirst()
                .orElse("unknown");
        if (PAYMENT_STAGE.equals(nextStage)) {
            return "等待 payment-core 上报 PAYMENT_SUCCESS";
        }
        if (CLEARING_STAGE.equals(nextStage)) {
            return "等待 clearing-system 上报 CLEARING_GENERATED";
        }
        if (SETTLEMENT_STAGE.equals(nextStage)) {
            return "等待 settlement-system 上报 SETTLEMENT_CREATED";
        }
        if (ACCOUNTING_STAGE.equals(nextStage)) {
            return "等待 accounting-system 上报 ACCOUNTING_POSTED";
        }
        return "等待下一阶段跨系统事实";
    }

    private void validateCrossSystemFact(CrossSystemFactRequestDTO request) {
        if (request == null
                || !StringUtils.hasText(request.getBusinessChainId())
                || !StringUtils.hasText(request.getSystemCode())
                || !StringUtils.hasText(request.getFactType())
                || !StringUtils.hasText(request.getFactNo())) {
            throw new BusinessException("业务链路 ID、来源系统、事实类型和事实编号不能为空");
        }
    }

    private boolean isAbnormalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.contains("FAIL")
                || normalized.contains("ERROR")
                || normalized.contains("ABNORMAL")
                || status.contains("失败")
                || status.contains("异常");
    }

    /**
     * 只有明确成功态才能推动关键阶段完成，避免将处理中或未知状态误判为已收口。
     */
    private boolean isSuccessfulStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return SUCCESS.equals(normalized)
                || "COMPLETED".equals(normalized)
                || "SUCCESSFUL".equals(normalized)
                || "成功".equals(status.trim())
                || "已完成".equals(status.trim());
    }

    @Transactional
    protected ReconciliationBatchListItemDTO executeRun(String batchNo) {
        dao.resetBatchResults(batchNo);
        List<ChannelRecordEntity> channels = dao.findChannelRecords(batchNo);
        List<InternalRecordEntity> internals = dao.findInternalRecords(batchNo);
        Map<String, ChannelRecordEntity> channelMap = new HashMap<>();
        for (ChannelRecordEntity item : channels) {
            channelMap.put(item.getPaymentOrderId(), item);
        }
        Map<String, InternalRecordEntity> internalMap = new HashMap<>();
        for (InternalRecordEntity item : internals) {
            internalMap.put(item.getPaymentOrderId(), item);
        }
        Set<String> keys = new HashSet<>();
        keys.addAll(channelMap.keySet());
        keys.addAll(internalMap.keySet());
        int matched = 0;
        int differences = 0;
        for (String key : keys) {
            ChannelRecordEntity channel = channelMap.get(key);
            InternalRecordEntity internal = internalMap.get(key);
            String differenceType = null;
            if (channel == null) {
                differenceType = "INTERNAL_ONLY";
            } else if (internal == null) {
                differenceType = "CHANNEL_ONLY";
            } else if (channel.getAmount().compareTo(internal.getAmount()) != 0) {
                differenceType = "AMOUNT_MISMATCH";
            } else if (!SUCCESS.equalsIgnoreCase(channel.getTradeStatus())
                    || !SUCCESS.equalsIgnoreCase(internal.getInternalStatus())) {
                differenceType = "STATUS_MISMATCH";
            }
            if (differenceType == null) {
                matched++;
            } else {
                differences++;
                dao.insertDifference("DIFF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                        batchNo, differenceType, key,
                        channel == null ? null : channel.getAmount(),
                        internal == null ? null : internal.getAmount());
            }
        }
        dao.updateBatchResult(batchNo, channels.size(), internals.size(), matched, differences);
        return requiredBatch(batchNo);
    }

    private ReconciliationBatchListItemDTO requiredBatch(String batchNo) {
        ReconciliationBatchListItemDTO batch = dao.findBatch(required(batchNo, "批次号不能为空"));
        if (batch == null) {
            throw new BusinessException("对账批次不存在");
        }
        return batch;
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    /**
     * 链路状态计算结果，集中承载状态机输出，避免在 DTO 组装过程中散落条件分支。
     */
    private static final class ChainEvaluation {

        private final String chainStatus;
        private final String responsibilityStatus;
        private final String nextAction;
        private final int abnormalFactCount;
        private final int completedStageCount;
        private final boolean financialCloseable;
        private final LocalDateTime lastFactAt;
        private final String diagnosis;

        private ChainEvaluation(String chainStatus,
                                String responsibilityStatus,
                                String nextAction,
                                int abnormalFactCount,
                                int completedStageCount,
                                boolean financialCloseable,
                                LocalDateTime lastFactAt,
                                String diagnosis) {
            this.chainStatus = chainStatus;
            this.responsibilityStatus = responsibilityStatus;
            this.nextAction = nextAction;
            this.abnormalFactCount = abnormalFactCount;
            this.completedStageCount = completedStageCount;
            this.financialCloseable = financialCloseable;
            this.lastFactAt = lastFactAt;
            this.diagnosis = diagnosis;
        }
    }

    /**
     * 批次准入计算结果，和批次表字段一一对应，便于审计与页面直接展示。
     */
    private static final class BatchReadinessEvaluation {

        private final String readinessStatus;
        private final String readinessSummary;
        private final String readinessSuggestedAction;
        private final String readinessBlockingOwner;
        private final int chainCount;
        private final int completedChainCount;
        private final int abnormalChainCount;
        private final int pendingChainCount;

        private BatchReadinessEvaluation(String readinessStatus,
                                         String readinessSummary,
                                         String readinessSuggestedAction,
                                         String readinessBlockingOwner,
                                         int chainCount,
                                         int completedChainCount,
                                         int abnormalChainCount,
                                         int pendingChainCount) {
            this.readinessStatus = readinessStatus;
            this.readinessSummary = readinessSummary;
            this.readinessSuggestedAction = readinessSuggestedAction;
            this.readinessBlockingOwner = readinessBlockingOwner;
            this.chainCount = chainCount;
            this.completedChainCount = completedChainCount;
            this.abnormalChainCount = abnormalChainCount;
            this.pendingChainCount = pendingChainCount;
        }
    }
}
