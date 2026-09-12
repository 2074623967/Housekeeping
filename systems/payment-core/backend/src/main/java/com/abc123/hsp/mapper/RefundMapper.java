package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundApplicationIdempotencyDTO;
import com.abc123.hsp.dto.RefundAttemptItemDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
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
    List<String> findFailedRefundOrderIds(@Param("batchSize") int batchSize);

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
     * 查询退款渠道尝试历史。
     */
    List<RefundAttemptItemDTO> findAttempts(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询并锁定可退款的原支付单快照。
     */
    RefundPaymentSourceDTO findPaymentSourceForUpdate(String paymentOrderId);

    /**
     * 按支付单与客户端幂等键查询既有退款申请。
     */
    RefundApplicationIdempotencyDTO findApplicationByIdempotencyKey(
            @Param("paymentOrderId") String paymentOrderId,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 查询并锁定失败退款重试额度快照。
     */
    RefundRetrySourceDTO findRetrySourceForUpdate(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询退款渠道下发请求快照。
     */
    RefundChannelSubmitRequestDTO findChannelSubmitRequestByRefundOrderId(@Param("refundOrderId") String refundOrderId);

    /**
     * 按尝试号查询退款渠道下发请求快照。
     */
    RefundChannelSubmitRequestDTO findChannelSubmitRequestByAttemptNo(
            @Param("refundAttemptNo") String refundAttemptNo);

    /**
     * 新建一轮退款渠道尝试。
     */
    int insertRefundAttempt(@Param("refundAttemptNo") String refundAttemptNo,
                            @Param("refundOrderId") String refundOrderId,
                            @Param("channelCode") String channelCode);

    /**
     * 查询并锁定退款回调收口快照。
     */
    RefundCallbackSourceDTO findCallbackSourceForUpdate(@Param("refundOrderId") String refundOrderId);

    /**
     * 查询退款主动查单快照，不持有行锁跨越渠道调用。
     */
    RefundCallbackSourceDTO findCallbackSourceByRefundOrderId(@Param("refundOrderId") String refundOrderId);

    /**
     * 以稳定事件号写入或重新激活退款渠道下发 outbox。
     */
    int upsertRefundSubmitEvent(@Param("eventNo") String eventNo,
                                @Param("refundAttemptNo") String refundAttemptNo,
                                @Param("paymentOrderId") String paymentOrderId);

    /**
     * 将当前尝试绑定为渠道已受理。
     */
    int bindRefundAttemptAccepted(@Param("refundAttemptNo") String refundAttemptNo,
                                  @Param("channelRefundNo") String channelRefundNo);

    /**
     * 将调用前失败的当前尝试标记为可重试失败。
     */
    int markRefundAttemptFailed(@Param("refundAttemptNo") String refundAttemptNo);

    /**
     * 将渠道调用结果未知的当前尝试标记为未知。
     */
    int markRefundAttemptUnknown(@Param("refundAttemptNo") String refundAttemptNo);

    /**
     * 将当前尝试收口为退款终态。
     */
    int finalizeRefundAttempt(@Param("refundAttemptNo") String refundAttemptNo,
                              @Param("attemptStatus") String attemptStatus,
                              @Param("attemptStatusType") String attemptStatusType);

    /**
     * 按渠道退款号查找所属尝试，用于拒绝旧尝试乱序回调。
     */
    String findAttemptNoByChannelRefundNo(@Param("channelCode") String channelCode,
                                          @Param("channelRefundNo") String channelRefundNo);

    /**
     * 首次绑定渠道退款受理事实，禁止后续补偿覆盖为其他渠道单号。
     */
    int bindRefundChannelAccepted(@Param("refundOrderId") String refundOrderId,
                                  @Param("refundAttemptNo") String refundAttemptNo,
                                  @Param("channelCode") String channelCode,
                                  @Param("channelRefundNo") String channelRefundNo);

    /**
     * 仅为仍处于 UNKNOWN 的退款提交首次补录可信渠道受理事实。
     */
    int bindUnknownRefundChannelAccepted(@Param("refundOrderId") String refundOrderId,
                                         @Param("refundAttemptNo") String refundAttemptNo,
                                         @Param("channelCode") String channelCode,
                                         @Param("channelRefundNo") String channelRefundNo);

    /**
     * 记录结果未知的退款提交实际调用渠道，不伪造渠道受理号。
     */
    int recordUnknownRefundSubmitChannel(@Param("refundOrderId") String refundOrderId,
                                         @Param("refundAttemptNo") String refundAttemptNo,
                                         @Param("channelCode") String channelCode);

    /**
     * 将已由可信查单或验签回调确认的退款提交未知事件收敛为成功。
     */
    int markUnknownRefundSubmitEventConfirmed(@Param("refundAttemptNo") String refundAttemptNo);

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
     * 将失败退款重新置为待渠道处理，并清除上一轮当前受理轨迹。
     */
    int restartFailedRefund(@Param("refundOrderId") String refundOrderId);

    /**
     * 新增退款操作日志。
     */
    int insertOperationLog(RefundOperationLogEntity entity);
}
