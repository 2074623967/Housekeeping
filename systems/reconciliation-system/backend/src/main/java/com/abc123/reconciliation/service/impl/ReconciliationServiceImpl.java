package com.abc123.reconciliation.service.impl;

import com.abc123.reconciliation.common.BusinessException;
import com.abc123.reconciliation.dao.ReconciliationDao;
import com.abc123.reconciliation.dto.ChannelRecordRequestDTO;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.DifferenceResolveRequestDTO;
import com.abc123.reconciliation.dto.InternalRecordRequestDTO;
import com.abc123.reconciliation.dto.PageResultDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchCreateRequestDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import com.abc123.reconciliation.service.ReconciliationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public ReconciliationServiceImpl(ReconciliationDao dao) {
        this.dao = dao;
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
    @Transactional
    public ReconciliationBatchListItemDTO run(String batchNo) {
        requiredBatch(batchNo);
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
}
