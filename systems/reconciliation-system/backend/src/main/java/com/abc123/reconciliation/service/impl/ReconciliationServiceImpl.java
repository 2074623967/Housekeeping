package com.abc123.reconciliation.service.impl;

import com.abc123.reconciliation.common.BusinessException;
import com.abc123.reconciliation.dao.ReconciliationDao;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
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
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("渠道账单记录字段不完整");
        }
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
        if (request == null || !StringUtils.hasText(request.getPaymentOrderId())
                || request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("平台支付事实字段不完整");
        }
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

    @Override
    public ReconciliationBatchListItemDTO run(String batchNo) {
        requiredBatch(batchNo);
        markBatchStatus(batchNo, RUNNING);
        return transactionTemplate.execute(status -> executeRun(batchNo));
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
        builder.append("批次号,业务日期,渠道编码,账单来源,批次状态,渠道记录数,平台记录数,已匹配数,差异数,未结案差异数,已结案差异数\n");
        builder.append(csv(detail.getBatchNo())).append(',')
                .append(csv(detail.getBusinessDate() == null ? null : detail.getBusinessDate().toString())).append(',')
                .append(csv(detail.getChannelCode())).append(',')
                .append(csv(detail.getBillSource())).append(',')
                .append(csv(detail.getStatus())).append(',')
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
    public List<ReconciliationBatchListItemDTO> batches() {
        return dao.findBatches();
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
        request.setBusinessChainId(request.getBusinessChainId().trim());
        request.setSystemCode(request.getSystemCode().trim());
        request.setFactType(request.getFactType().trim());
        request.setFactNo(request.getFactNo().trim());
        request.setBusinessNo(required(request.getBusinessNo(), "来源业务编号不能为空"));
        request.setFactStatus(required(request.getFactStatus(), "事实状态不能为空"));
        if (request.getOccurredAt() == null) {
            request.setOccurredAt(LocalDateTime.now());
        }
        dao.insertCrossSystemFact(request);
        return crossSystemChain(request.getBusinessChainId());
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
        boolean abnormal = facts.stream().anyMatch(item -> isAbnormalStatus(item.getFactStatus()));
        snapshot.setHasAbnormalFact(abnormal);
        if (facts.isEmpty()) {
            snapshot.setDiagnosis("未接收到任何跨系统事实");
        } else if (abnormal) {
            snapshot.setDiagnosis("链路存在失败或异常事实，禁止直接进入财务收口");
        } else {
            snapshot.setDiagnosis("已接收跨系统事实，当前未发现失败状态");
        }
        return snapshot;
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

    private void markBatchStatus(String batchNo, String status) {
        if (dao.updateBatchStatus(batchNo, status) == 0) {
            throw new BusinessException("对账批次不存在");
        }
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
}
