package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentChannelQueryHealthDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentSubmitConcurrencyTokenDTO;
import com.abc123.hsp.dto.PaymentAttemptIdempotencyDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付核心域数据访问接口，负责支付单、预付单、账单和轨迹表读写。
 */
public interface PaymentMapper {

    /**
     * 查询支付单列表。
     */
    List<PaymentListItemDTO> findAll(@Param("query") PaymentListQueryDTO query);

    /**
     * 查询导出使用的支付单列表。
     */
    List<PaymentListItemDTO> findAllForExport(@Param("query") PaymentListQueryDTO query);

    /**
     * 统计符合条件的支付单总数。
     */
    long count(@Param("query") PaymentListQueryDTO query);

    /**
     * 查询已经超过收银台失效时间且仍未收口的支付单。
     */
    List<ExpiredPaymentDTO> findExpiredPayments(@Param("batchSize") int batchSize);

    /**
     * 原子登记超时关单失败并推进下次重试时间。
     */
    int recordExpiryCloseFailure(@Param("paymentOrderId") String paymentOrderId,
                                 @Param("errorMessage") String errorMessage,
                                 @Param("initialIntervalSeconds") int initialIntervalSeconds,
                                 @Param("backoffFactor") int backoffFactor,
                                 @Param("maxIntervalSeconds") int maxIntervalSeconds);

    /**
     * 清除已经收口或已失去关单资格的支付单重试标记。
     */
    int deleteExpiryCloseRetry(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 有界清理已经失去超时关单资格的重试标记。
     */
    int deleteStaleExpiryCloseRetries(@Param("batchSize") int batchSize);

    /**
     * 查询支付单详情。
     */
    PaymentDetailDTO findDetail(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 查询支付单状态与最近一次渠道提交事实。
     */
    PaymentDetailDTO findSubmissionFact(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 幂等初始化订单维度的预付单创建协调行。
     */
    int initPrepayCreationLock(@Param("orderNo") String orderNo);

    /**
     * 在当前事务内锁定订单维度的预付单创建协调行。
     */
    String lockPrepayCreation(@Param("orderNo") String orderNo);

    /**
     * 查询订单应收金额。
     */
    BigDecimal findOrderAmount(@Param("orderNo") String orderNo);

    /**
     * 查询订单已支付金额。
     */
    BigDecimal findPaidAmount(@Param("orderNo") String orderNo);

    /**
     * 查询订单客户名称。
     */
    String findCustomerNameByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询订单服务者名称。
     */
    String findWorkerNameByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询订单关联账单号。
     */
    String findBillNoByOrderNo(@Param("orderNo") String orderNo);

    int countMatchingStandardBill(@Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("orderAmount") BigDecimal orderAmount,
            @Param("paidAmount") BigDecimal paidAmount);

    /**
     * 新增账单。
     */
    int insertBill(@Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("orderAmount") BigDecimal orderAmount,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("billStatus") String billStatus,
            @Param("billStatusType") String billStatusType);

    /**
     * 新增支付单。
     */
    int insertPaymentOrder(@Param("paymentOrderId") String paymentOrderId,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("channelCode") String channelCode);

    /**
     * 新增预付单。
     */
    int insertPrepayOrder(@Param("prepayOrderNo") String prepayOrderNo,
            @Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("payScene") String payScene,
            @Param("cashierTitle") String cashierTitle,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("sourceKind") String sourceKind);

    /**
     * 查询预付单。
     */
    PrepayOrderDTO findPrepay(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 查询预付单是否已过期且仍处于未提交支付状态。
     */
    boolean isPrepayExpired(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 查询订单当前可复用的有效预付单。
     */
    PrepayOrderDTO findLatestActivePrepayByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 查询支付回调日志列表。
     */
    List<String> findNotifyLogs(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 查询支付路由日志列表。
     */
    List<String> findRouteLogs(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 查询支付事件列表。
     */
    List<String> findEventItems(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 根据预付单查询支付单号。
     */
    String findPaymentOrderIdByPrepayOrderNo(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 根据预付单查询订单号。
     */
    String findOrderNoByPrepayOrderNo(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 渠道下发前写入风控状态，不改变渠道交易号。
     */
    int updatePaymentRiskStatus(@Param("paymentOrderId") String paymentOrderId,
            @Param("status") String status,
            @Param("statusType") String statusType);

    /**
     * 可信渠道结果确认成功后原子抢占状态收口权，避免并发回调或查单重复记账和重复发事件。
     */
    int markPaymentSuccessFromChannelResult(@Param("paymentOrderId") String paymentOrderId,
                                            @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 将支付单最新一次支付尝试原子终结为成功，并保留首次终态完成时间。
     */
    int finalizeLatestPaymentAttemptSuccess(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 非成功渠道回调原子维持待确认状态，禁止并发覆盖已经成功或关闭的终态。
     */
    int markPaymentPendingFromCallback(@Param("paymentOrderId") String paymentOrderId,
                                       @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 原子关闭从未向渠道提交的过期支付单，避免与并发支付提交互相覆盖。
     */
    int markExpiredUnsubmittedPaymentClosed(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 人工原子关闭尚未向渠道提交的支付单。
     */
    int markUnsubmittedPaymentClosed(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 更新预付单为支付中状态。
     */
    int updatePrepayToPaying(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 按支付单更新收银台状态。
     */
    int updatePrepayStatusByPaymentOrderId(@Param("paymentOrderId") String paymentOrderId,
            @Param("cashierStatus") String cashierStatus,
            @Param("cashierStatusType") String cashierStatusType);

    /**
     * 回写支付方式和渠道编码。
     */
    int updatePaymentMethodAndChannel(@Param("paymentOrderId") String paymentOrderId,
            @Param("prepayOrderNo") String prepayOrderNo,
            @Param("paymentMethod") String paymentMethod,
            @Param("channelCode") String channelCode,
            @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 渠道提交返回后补充支付单渠道事实，只允许待回调状态且不得覆盖已绑定流水。
     */
    int bindPaymentChannelSubmitResult(@Param("paymentOrderId") String paymentOrderId,
            @Param("attemptNo") String attemptNo,
            @Param("paymentMethod") String paymentMethod,
            @Param("channelCode") String channelCode,
            @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 渠道提交结果不确定时原子标记待查单，禁止覆盖成功、关闭或冲突流水。
     */
    int markPaymentSubmitResultUnknown(@Param("paymentOrderId") String paymentOrderId,
            @Param("attemptNo") String attemptNo,
            @Param("paymentMethod") String paymentMethod,
            @Param("channelTransactionNo") String channelTransactionNo);

    boolean existsOtherPaymentByChannelTransactionNo(@Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 校验终态快回调属于当前支付单的末次尝试。
     */
    boolean isLatestPaymentAttemptForOrder(@Param("paymentOrderId") String paymentOrderId,
                                           @Param("attemptNo") String attemptNo);

    /**
     * 保存最近一次渠道返回码标准化快照，供详情与排障稳定读取。
     */
    int updatePaymentReturnCodeSnapshot(
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelReturnCode") String channelReturnCode,
            @Param("standardizedCode") String standardizedCode,
            @Param("standardizedMessage") String standardizedMessage,
            @Param("handlingSuggestion") String handlingSuggestion,
            @Param("retryable") String retryable,
            @Param("manualInterventionRequired") String manualInterventionRequired,
            @Param("mappingVersion") String mappingVersion,
            @Param("mappingMatched") Boolean mappingMatched);

    /**
     * 回写支付单的最新一次支付尝试状态，保留历史尝试事实。
     */
    int updateLatestPaymentAttemptStatus(@Param("paymentOrderId") String paymentOrderId,
            @Param("attemptStatus") String attemptStatus,
            @Param("attemptStatusType") String attemptStatusType);

    /**
     * 支付成功后更新订单已支付金额和订单状态。
     */
    int updateOrderAfterPayment(@Param("orderNo") String orderNo,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("orderStatus") String orderStatus,
            @Param("orderStatusType") String orderStatusType,
            @Param("expectedPaidAmount") BigDecimal expectedPaidAmount,
            @Param("expectedCustomerName") String expectedCustomerName);

    /**
     * 支付成功后更新账单已支付金额和账单状态。
     */
    int updateBillAfterPayment(@Param("billNo") String billNo,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("billStatus") String billStatus,
            @Param("billStatusType") String billStatusType,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("expectedPaidAmount") BigDecimal expectedPaidAmount);

    /**
     * 写入支付回调日志。
     */
    int insertNotifyLog(@Param("notifyNo") String notifyNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("notifyType") String notifyType,
            @Param("notifyPayload") String notifyPayload,
            @Param("notifyResult") String notifyResult,
            @Param("notifyStatus") String notifyStatus,
            @Param("notifyStatusType") String notifyStatusType);

    /**
     * 写入支付路由日志。
     */
    int insertRouteRecord(@Param("routeNo") String routeNo,
            @Param("attemptNo") String attemptNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("routeRule") String routeRule,
            @Param("routeResult") String routeResult);

    /**
     * 写入支付事件。
     */
    int insertEvent(@Param("eventNo") String eventNo,
            @Param("eventType") String eventType,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("bizNo") String bizNo,
            @Param("eventPayload") String eventPayload);

    /**
     * 写入支付尝试记录。
     */
    int insertPaymentAttempt(@Param("attemptNo") String attemptNo,
            @Param("prepayOrderNo") String prepayOrderNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("paymentMethod") String paymentMethod,
            @Param("sourceAppId") String sourceAppId,
            @Param("terminal") String terminal,
            @Param("clientIp") String clientIp,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestPayload") String requestPayload,
            @Param("responsePayload") String responsePayload,
            @Param("attemptStatus") String attemptStatus,
            @Param("attemptStatusType") String attemptStatusType,
            @Param("requestFingerprint") String requestFingerprint);

    /**
     * 按支付尝试号回写渠道提交结果，避免覆盖同一支付单的历史尝试。
     */
    int updatePaymentAttemptSubmitResult(@Param("attemptNo") String attemptNo,
                                         @Param("responsePayload") String responsePayload,
                                         @Param("attemptStatus") String attemptStatus,
                                         @Param("attemptStatusType") String attemptStatusType);

    int recordPaymentAttemptGateway(@Param("attemptNo") String attemptNo,
                                    @Param("gatewayCode") String gatewayCode);

    /**
     * 写入独立支付处理日志；相同来源事实重复写入时保持幂等。
     */
    int insertPaymentProcessLog(PaymentProcessLogEntity entity);

    /**
     * 回写路由记录实际消费的运营配置快照版本。
     */
    int updateRouteOpsConfigSnapshotVersion(@Param("routeNo") String routeNo,
                                             @Param("snapshotVersion") Long snapshotVersion);

    /**
     * 回写支付尝试实际消费的运营配置快照版本。
     */
    int updateAttemptOpsConfigSnapshotVersion(@Param("attemptNo") String attemptNo,
                                               @Param("snapshotVersion") Long snapshotVersion);

    /**
     * 判断幂等键对应的支付尝试是否已经存在。
     */
    boolean existsPaymentAttemptByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 查询幂等键已归属的预付单和支付单，防止跨支付单复用客户端幂等键。
     */
    PaymentAttemptIdempotencyDTO findPaymentAttemptIdempotency(
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 查询来源应用对应的生效控制策略。
     */
    PaymentControlPolicyDTO findActiveControlPolicyBySourceAppId(@Param("sourceAppId") String sourceAppId);

    /**
     * 查询支付提交并发令牌。
     */
    PaymentSubmitConcurrencyTokenDTO findSubmitConcurrencyToken(@Param("prepayOrderNo") String prepayOrderNo,
                                                                @Param("sourceAppId") String sourceAppId);

    boolean hasActiveSubmitConcurrencyToken(@Param("paymentOrderId") String paymentOrderId);

    /**
     * 新增支付提交并发令牌。
     */
    int insertSubmitConcurrencyToken(@Param("prepayOrderNo") String prepayOrderNo,
                                     @Param("paymentOrderId") String paymentOrderId,
                                     @Param("sourceAppId") String sourceAppId,
                                     @Param("holderIdempotencyKey") String holderIdempotencyKey,
                                     @Param("holderTerminal") String holderTerminal,
                                     @Param("holderClientIp") String holderClientIp,
                                     @Param("expireAfterSeconds") int expireAfterSeconds);

    /**
     * 占用已存在的支付提交并发令牌。
     */
    int occupySubmitConcurrencyToken(@Param("prepayOrderNo") String prepayOrderNo,
                                     @Param("paymentOrderId") String paymentOrderId,
                                     @Param("sourceAppId") String sourceAppId,
                                     @Param("holderIdempotencyKey") String holderIdempotencyKey,
                                     @Param("holderTerminal") String holderTerminal,
                                     @Param("holderClientIp") String holderClientIp,
                                     @Param("expireAfterSeconds") int expireAfterSeconds);

    /**
     * 释放支付提交并发令牌。
     */
    int releaseSubmitConcurrencyToken(@Param("paymentOrderId") String paymentOrderId,
                                      @Param("releaseReason") String releaseReason);

    /**
     * 查询指定渠道当天已受理的交易金额，用于支付控制限额校验。
     */
    BigDecimal sumTodayAcceptedAmountByChannel(@Param("channelCode") String channelCode);

    /**
     * 按渠道聚合最近窗口内的主动查单成功与失败事实，供路由健康熔断使用。
     */
    List<PaymentChannelQueryHealthDTO> findRecentChannelQueryHealth(
            @Param("lookbackHours") int lookbackHours);
}
