package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentDayEndBatchListItemDTO;
import com.abc123.hsp.dto.PaymentDayEndOverviewDTO;
import com.abc123.hsp.entity.PaymentDayEndBatchEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付日终处理 Mapper。
 */
public interface PaymentDayEndMapper {

    /**
     * 查询日终处理总览指标。
     */
    PaymentDayEndOverviewDTO findOverviewSummary();

    /**
     * 查询最近日终批次。
     */
    List<PaymentDayEndBatchListItemDTO> findRecentBatches();

    /**
     * 查询指定业务日的支付总单量。
     */
    Integer countPaymentsByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的支付成功单量。
     */
    Integer countSuccessPaymentsByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的支付成功金额。
     */
    BigDecimal sumSuccessPaymentAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的退款成功单量。
     */
    Integer countSuccessRefundsByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日渠道侧已成功收口的单量。
     */
    Integer countChannelSuccessByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日渠道侧已成功收口的金额。
     */
    BigDecimal sumChannelSuccessAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日内部事件已成功发布的单量。
     */
    Integer countInternalSuccessByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日内部事件已成功发布的金额。
     */
    BigDecimal sumInternalSuccessAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日支付成功但仍存在差异的单量。
     */
    Integer countPaymentSuccessGapByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日支付成功但仍存在差异的金额。
     */
    BigDecimal sumPaymentSuccessGapAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的退款成功金额。
     */
    BigDecimal sumSuccessRefundAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的渠道侧异常数。
     */
    Integer countChannelAbnormalByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的内部事件异常数。
     */
    Integer countInternalAbnormalByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的待收口退款数。
     */
    Integer countPendingRefundByDate(@Param("bizDate") String bizDate);

    /**
     * 查询指定业务日的待收口退款金额。
     */
    BigDecimal sumPendingRefundAmountByDate(@Param("bizDate") String bizDate);

    /**
     * 新增支付日终批次。
     */
    int insertBatch(PaymentDayEndBatchEntity entity);
}
