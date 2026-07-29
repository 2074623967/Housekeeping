package com.abc123.clearing.mapper;

import com.abc123.clearing.entity.ClearingBatchEntity;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ClearingRuleEntity;
import com.abc123.clearing.entity.FeeRuleEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 清分系统持久化 Mapper。
 */
public interface ClearingDataMapper {

    /**
     * 统计批次数量。
     */
    long countBatches();

    /**
     * 查询批次列表。
     */
    List<ClearingBatchEntity> findBatches();

    /**
     * 查询批次。
     */
    ClearingBatchEntity findBatch(@Param("batchNo") String batchNo);

    /**
     * 查询批次幂等键。
     */
    ClearingBatchEntity findBatchByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 新增批次。
     */
    int insertBatch(ClearingBatchEntity entity);

    /**
     * 更新批次统计和状态。
     */
    int updateBatch(@Param("batchNo") String batchNo,
                    @Param("totalOrderCount") int totalOrderCount,
                    @Param("successOrderCount") int successOrderCount,
                    @Param("failedOrderCount") int failedOrderCount,
                    @Param("totalAmount") BigDecimal totalAmount,
                    @Param("versionNo") String versionNo,
                    @Param("batchStatus") String batchStatus,
                    @Param("finishedAt") String finishedAt);

    /**
     * 查询清分结果列表。
     */
    List<ClearingOrderEntity> findOrders();

    /**
     * 查询清分结果。
     */
    ClearingOrderEntity findOrder(@Param("clearingNo") String clearingNo);

    /**
     * 新增清分结果。
     */
    int insertOrder(ClearingOrderEntity entity);

    /**
     * 查询清分规则列表。
     */
    List<ClearingRuleEntity> findRules();

    /**
     * 查询清分规则。
     */
    ClearingRuleEntity findRule(@Param("ruleNo") String ruleNo);

    /**
     * 新增清分规则。
     */
    int insertRule(ClearingRuleEntity entity);

    /**
     * 更新清分规则状态。
     */
    int updateRuleStatus(@Param("ruleNo") String ruleNo, @Param("ruleStatus") String ruleStatus);

    /**
     * 查询费用规则列表。
     */
    List<FeeRuleEntity> findFeeRules();

    /**
     * 新增费用规则。
     */
    int insertFeeRule(FeeRuleEntity entity);

    /**
     * 查询分账明细列表。
     */
    List<ShareItemEntity> findShares();

    /**
     * 查询某笔清分单对应的分账明细。
     */
    List<ShareItemEntity> findSharesByClearingNo(@Param("clearingNo") String clearingNo);

    /**
     * 新增分账明细。
     */
    int insertShare(ShareItemEntity entity);

    /**
     * 查询事件列表。
     */
    List<ClearingEventEntity> findEvents();

    /**
     * 新增事件。
     */
    int insertEvent(ClearingEventEntity entity);
}
