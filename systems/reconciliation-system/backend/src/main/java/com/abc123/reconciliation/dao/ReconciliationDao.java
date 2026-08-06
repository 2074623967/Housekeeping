package com.abc123.reconciliation.dao;

import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import java.math.BigDecimal;
import java.util.List;

/**
 * 对账数据访问编排层。
 */
public interface ReconciliationDao {

    int insertBatch(String batchNo, String businessDate, String channelCode, String billSource);

    ReconciliationBatchListItemDTO findBatch(String batchNo);

    List<ReconciliationBatchListItemDTO> findBatches();

    int insertChannelRecord(String batchNo, ChannelRecordEntity record);

    int insertInternalRecord(String batchNo, InternalRecordEntity record);

    List<ChannelRecordEntity> findChannelRecords(String batchNo);

    List<InternalRecordEntity> findInternalRecords(String batchNo);

    int updateBatchStatus(String batchNo, String status);

    int resetBatchResults(String batchNo);

    int insertDifference(String differenceNo, String batchNo, String differenceType, String paymentOrderId,
                         BigDecimal channelAmount, BigDecimal internalAmount);

    int updateBatchResult(String batchNo, int channelCount, int internalCount, int matchedCount, int differenceCount);

    List<ReconciliationDifferenceDTO> findDifferences(DifferenceQueryDTO query);

    long countDifferences(DifferenceQueryDTO query);

    int resolveDifference(String differenceNo, String resolution, String remark);

    ReconciliationOverviewDTO overview();
}
