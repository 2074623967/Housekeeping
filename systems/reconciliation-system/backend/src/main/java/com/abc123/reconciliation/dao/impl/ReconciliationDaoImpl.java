package com.abc123.reconciliation.dao.impl;

import com.abc123.reconciliation.dao.ReconciliationDao;
import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import com.abc123.reconciliation.mapper.ReconciliationMapper;
import java.math.BigDecimal;
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
    public List<ReconciliationBatchListItemDTO> findBatches() {
        return mapper.findBatches();
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
        return mapper.findChannelRecords(batchNo);
    }

    @Override
    public List<InternalRecordEntity> findInternalRecords(String batchNo) {
        return mapper.findInternalRecords(batchNo);
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
    public List<ReconciliationDifferenceDTO> findDifferences(DifferenceQueryDTO query) {
        return mapper.findDifferences(query);
    }

    @Override
    public long countDifferences(DifferenceQueryDTO query) {
        return mapper.countDifferences(query);
    }

    @Override
    public int resolveDifference(String differenceNo, String resolution, String remark) {
        return mapper.resolveDifference(differenceNo, resolution, remark);
    }

    @Override
    public ReconciliationOverviewDTO overview() {
        return mapper.overview();
    }
}
