package com.abc123.reconciliation.dao.impl;

import com.abc123.reconciliation.dao.ReconciliationDao;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.BatchQueryDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchChannelRecordDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchInternalRecordDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.dto.CrossSystemFactDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import com.abc123.reconciliation.mapper.ReconciliationMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * MyBatis 数据访问编排实现。
 */
@Repository
public class ReconciliationDaoImpl implements ReconciliationDao {

    private final ReconciliationMapper mapper;

    public ReconciliationDaoImpl(ReconciliationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insertBatch(String batchNo, String businessDate, String channelCode, String billSource) {
        return mapper.insertBatch(batchNo, businessDate, channelCode, billSource);
    }

    @Override
    public ReconciliationBatchListItemDTO findBatch(String batchNo) {
        return mapper.findBatch(batchNo);
    }

    @Override
    public List<ReconciliationBatchListItemDTO> findBatches(BatchQueryDTO query) {
        return safeList(mapper.findBatches(query));
    }

    @Override
    public long countBatches() {
        return mapper.countBatches();
    }

    @Override
    public int insertChannelRecord(String batchNo, ChannelRecordEntity record) {
        return mapper.insertChannelRecord(batchNo, record);
    }

    @Override
    public int insertInternalRecord(String batchNo, InternalRecordEntity record) {
        return mapper.insertInternalRecord(batchNo, record);
    }

    @Override
    public List<ChannelRecordEntity> findChannelRecords(String batchNo) {
        return safeList(mapper.findChannelRecords(batchNo));
    }

    @Override
    public List<InternalRecordEntity> findInternalRecords(String batchNo) {
        return safeList(mapper.findInternalRecords(batchNo));
    }

    @Override
    public List<ReconciliationBatchChannelRecordDTO> findChannelRecordDetails(String batchNo) {
        return safeList(mapper.findChannelRecordDetails(batchNo));
    }

    @Override
    public List<ReconciliationBatchInternalRecordDTO> findInternalRecordDetails(String batchNo) {
        return safeList(mapper.findInternalRecordDetails(batchNo));
    }

    @Override
    public int updateBatchStatus(String batchNo, String status) {
        return mapper.updateBatchStatus(batchNo, status);
    }

    @Override
    public int resetBatchResults(String batchNo) {
        return mapper.resetBatchResults(batchNo);
    }

    @Override
    public int insertDifference(String differenceNo, String batchNo, String differenceType, String paymentOrderId,
                                 BigDecimal channelAmount, BigDecimal internalAmount) {
        return mapper.insertDifference(differenceNo, batchNo, differenceType, paymentOrderId,
                channelAmount, internalAmount);
    }

    @Override
    public int updateBatchResult(String batchNo, int channelCount, int internalCount,
                                 int matchedCount, int differenceCount) {
        return mapper.updateBatchResult(batchNo, channelCount, internalCount, matchedCount, differenceCount);
    }

    @Override
    public int updateBatchReadiness(String batchNo, String readinessStatus, String readinessSummary,
                                    String readinessSuggestedAction, String readinessBlockingOwner,
                                    int readinessChainCount, int readinessCompletedChainCount,
                                    int readinessAbnormalChainCount, int readinessPendingChainCount) {
        return mapper.updateBatchReadiness(batchNo, readinessStatus, readinessSummary,
                readinessSuggestedAction, readinessBlockingOwner, readinessChainCount,
                readinessCompletedChainCount, readinessAbnormalChainCount, readinessPendingChainCount);
    }

    @Override
    public List<ReconciliationDifferenceDTO> findDifferences(DifferenceQueryDTO query) {
        return safeList(mapper.findDifferences(query));
    }

    @Override
    public long countDifferences(DifferenceQueryDTO query) {
        return mapper.countDifferences(query);
    }

    @Override
    public List<ReconciliationDifferenceDTO> findDifferencesByBatch(String batchNo) {
        return safeList(mapper.findDifferencesByBatch(batchNo));
    }

    @Override
    public long countDifferencesByBatchAndStatus(String batchNo, String status) {
        return mapper.countDifferencesByBatchAndStatus(batchNo, status);
    }

    @Override
    public int resolveDifference(String differenceNo, String resolution, String remark) {
        return mapper.resolveDifference(differenceNo, resolution, remark);
    }

    @Override
    public ReconciliationOverviewDTO overview() {
        return mapper.overview();
    }

    @Override
    public int insertCrossSystemFact(CrossSystemFactRequestDTO request) {
        return mapper.insertCrossSystemFact(request);
    }

    @Override
    public List<CrossSystemFactDTO> findCrossSystemFacts(String businessChainId) {
        return safeList(mapper.findCrossSystemFacts(businessChainId));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
