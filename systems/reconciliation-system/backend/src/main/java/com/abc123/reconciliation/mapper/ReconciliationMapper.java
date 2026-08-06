package com.abc123.reconciliation.mapper;

import com.abc123.reconciliation.dto.DifferenceQueryDTO;
import com.abc123.reconciliation.dto.ReconciliationBatchListItemDTO;
import com.abc123.reconciliation.dto.ReconciliationDifferenceDTO;
import com.abc123.reconciliation.dto.ReconciliationOverviewDTO;
import com.abc123.reconciliation.entity.ChannelRecordEntity;
import com.abc123.reconciliation.entity.InternalRecordEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 对账 MyBatis Mapper。
 */
@Mapper
public interface ReconciliationMapper {

    int insertBatch(@Param("batchNo") String batchNo, @Param("businessDate") String businessDate,
                    @Param("channelCode") String channelCode, @Param("billSource") String billSource);

    ReconciliationBatchListItemDTO findBatch(@Param("batchNo") String batchNo);

    List<ReconciliationBatchListItemDTO> findBatches();

    int insertChannelRecord(@Param("batchNo") String batchNo, @Param("record") ChannelRecordEntity record);

    int insertInternalRecord(@Param("batchNo") String batchNo, @Param("record") InternalRecordEntity record);

    List<ChannelRecordEntity> findChannelRecords(@Param("batchNo") String batchNo);

    List<InternalRecordEntity> findInternalRecords(@Param("batchNo") String batchNo);

    int updateBatchStatus(@Param("batchNo") String batchNo, @Param("status") String status);

    int resetBatchResults(@Param("batchNo") String batchNo);

    int insertDifference(@Param("differenceNo") String differenceNo, @Param("batchNo") String batchNo,
                         @Param("differenceType") String differenceType,
                         @Param("paymentOrderId") String paymentOrderId,
                         @Param("channelAmount") BigDecimal channelAmount,
                         @Param("internalAmount") BigDecimal internalAmount);

    int updateBatchResult(@Param("batchNo") String batchNo, @Param("channelCount") int channelCount,
                          @Param("internalCount") int internalCount, @Param("matchedCount") int matchedCount,
                          @Param("differenceCount") int differenceCount);

    List<ReconciliationDifferenceDTO> findDifferences(DifferenceQueryDTO query);

    long countDifferences(DifferenceQueryDTO query);

    int resolveDifference(@Param("differenceNo") String differenceNo, @Param("resolution") String resolution,
                          @Param("remark") String remark);

    ReconciliationOverviewDTO overview();
}
