package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundOperationLogItemDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundRetrySourceDTO;
import com.abc123.hsp.entity.RefundOperationLogEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RefundMapper {

    /**
     * 查询退款单分页列表。
     */
    List<RefundListItemDTO> findAll(@Param("query") RefundQueryDTO query);

    /**
     * 查询导出使用的退款单列表。
     */
    List<RefundListItemDTO> findAllForExport(@Param("query") RefundQueryDTO query);

    /**
     * 统计符合条件的退款单总数。
     */
    long count(@Param("query") RefundQueryDTO query);

    /**
     * 查询失败退款单号列表。
     */
    List<String> findFailedRefundOrderIds();

    /**
     * 查询退款单详情。
     */
    RefundListItemDTO findByRefundOrderId(String refundOrderId);

    /**
     * 查询退款详情聚合信息。
     */
    RefundDetailDTO findDetailByRefundOrderId(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询退款操作日志。
     */
    List<RefundOperationLogItemDTO> findOperationLogs(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询并锁定可退款的原支付单快照。
     */
    RefundPaymentSourceDTO findPaymentSourceForUpdate(String paymentOrderId);

    /**
     * 查询并锁定失败退款重试额度快照。
     */
    RefundRetrySourceDTO findRetrySourceForUpdate(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询退款渠道下发请求快照。
     */
    RefundChannelSubmitRequestDTO findChannelSubmitRequestByRefundOrderId(@Param("refundOrderId") String refundOrderId);

    /**
     * 以稳定事件号写入或重新激活退款渠道下发 outbox。
     */
    int upsertRefundSubmitEvent(@Param("eventNo") String eventNo,
                                @Param("refundOrderId") String refundOrderId,
                                @Param("paymentOrderId") String paymentOrderId);

    /**
     * 首次绑定渠道退款受理事实，禁止后续补偿覆盖为其他渠道单号。
     */
    int bindRefundChannelAccepted(@Param("refundOrderId") String refundOrderId,
                                  @Param("channelCode") String channelCode,
                                  @Param("channelRefundNo") String channelRefundNo);

    /**
     * 汇总原支付单已申请且未终止的退款金额。
     */
    BigDecimal sumActiveRefundAmount(String paymentOrderId);

    /**
     * 插入退款申请单。
     */
    void insertRefund(@Param("refundOrderId") String refundOrderId,
                      @Param("source") RefundPaymentSourceDTO source,
                      @Param("request") RefundApplyRequestDTO request,
                      @Param("status") String status,
                      @Param("statusType") String statusType);

    /**
     * 更新退款单状态。
     */
    int updateRefundStatus(@Param("refundOrderId") String refundOrderId,
                           @Param("fromStatus") String fromStatus,
                           @Param("status") String status,
                           @Param("statusType") String statusType,
                           @Param("success") boolean success);

    /**
     * 新增退款操作日志。
     */
    int insertOperationLog(RefundOperationLogEntity entity);
}
