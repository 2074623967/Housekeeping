package com.abc123.settlement.mapper;

import com.abc123.settlement.entity.PayoutBatchEntity;
import com.abc123.settlement.entity.PayoutRecordEntity;
import com.abc123.settlement.entity.SettlementAuditLogEntity;
import com.abc123.settlement.entity.SettlementBatchEntity;
import com.abc123.settlement.entity.SettlementEventEntity;
import com.abc123.settlement.entity.SettlementItemEntity;
import com.abc123.settlement.entity.SettlementOrderEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 结算系统持久化 Mapper。
 */
public interface SettlementDataMapper {

    /**
     * 统计批次数量。
     */
    long countBatches();

    /**
     * 查询结算批次列表。
     */
    List<SettlementBatchEntity> findBatches();

    /**
     * 查询结算批次。
     */
    SettlementBatchEntity findBatch(@Param("batchNo") String batchNo);

    /**
     * 通过幂等键查询批次。
     */
    SettlementBatchEntity findBatchByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 新增结算批次。
     */
    int insertBatch(SettlementBatchEntity entity);

    /**
     * 更新结算批次统计和状态。
     */
    int updateBatch(@Param("batchNo") String batchNo,
                    @Param("totalCount") int totalCount,
                    @Param("auditedCount") int auditedCount,
                    @Param("payoutCount") int payoutCount,
                    @Param("totalAmount") BigDecimal totalAmount,
                    @Param("payoutChannel") String payoutChannel,
                    @Param("batchStatus") String batchStatus,
                    @Param("finishedAt") String finishedAt);

    /**
     * 查询结算单列表。
     */
    List<SettlementOrderEntity> findOrders();

    /**
     * 查询结算单。
     */
    SettlementOrderEntity findOrder(@Param("settlementNo") String settlementNo);

    /**
     * 新增结算单。
     */
    int insertOrder(SettlementOrderEntity entity);

    /**
     * 更新结算单状态。
     */
    int updateOrderStatus(@Param("settlementNo") String settlementNo,
                          @Param("settlementStatus") String settlementStatus,
                          @Param("payoutStatus") String payoutStatus,
                          @Param("auditStatus") String auditStatus);

    /**
     * 查询结算明细项。
     */
    List<SettlementItemEntity> findItemsBySettlementNo(@Param("settlementNo") String settlementNo);

    /**
     * 新增结算明细项。
     */
    int insertItem(SettlementItemEntity entity);

    /**
     * 查询审核日志。
     */
    List<SettlementAuditLogEntity> findAuditLogsBySettlementNo(@Param("settlementNo") String settlementNo);

    /**
     * 新增审核日志。
     */
    int insertAuditLog(SettlementAuditLogEntity entity);

    /**
     * 查询出款批次。
     */
    List<PayoutBatchEntity> findPayoutBatches();

    /**
     * 查询单个出款批次。
     */
    PayoutBatchEntity findPayoutBatch(@Param("payoutBatchNo") String payoutBatchNo);

    /**
     * 新增出款批次。
     */
    int insertPayoutBatch(PayoutBatchEntity entity);

    /**
     * 更新出款批次。
     */
    int updatePayoutBatch(@Param("payoutBatchNo") String payoutBatchNo,
                          @Param("payoutStatus") String payoutStatus,
                          @Param("payoutCount") int payoutCount,
                          @Param("successCount") int successCount,
                          @Param("failedCount") int failedCount,
                          @Param("totalAmount") BigDecimal totalAmount,
                          @Param("finishedAt") String finishedAt);

    /**
     * 查询出款记录列表。
     */
    List<PayoutRecordEntity> findPayoutRecordsByBatchNo(@Param("payoutBatchNo") String payoutBatchNo);

    /**
     * 新增出款记录。
     */
    int insertPayoutRecord(PayoutRecordEntity entity);

    /**
     * 更新出款记录状态。
     */
    int updatePayoutRecord(@Param("payoutNo") String payoutNo,
                           @Param("payoutStatus") String payoutStatus,
                           @Param("retryCount") int retryCount);

    /**
     * 查询事件列表。
     */
    List<SettlementEventEntity> findEvents();

    /**
     * 新增事件。
     */
    int insertEvent(SettlementEventEntity entity);
}
