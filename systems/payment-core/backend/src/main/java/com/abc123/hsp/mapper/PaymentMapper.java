package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
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
    List<PaymentListItemDTO> findAll();

    /**
     * 查询已经超过收银台失效时间且仍未收口的支付单。
     */
    List<ExpiredPaymentDTO> findExpiredPayments();

    /**
     * 查询支付单详情。
     */
    PaymentDetailDTO findDetail(@Param("paymentOrderId") String paymentOrderId);

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
     * 查询订单关联账单号。
     */
    String findBillNoByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 新增账单。
     */
    void insertBill(@Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("orderAmount") BigDecimal orderAmount,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("billStatus") String billStatus,
            @Param("billStatusType") String billStatusType);

    /**
     * 新增支付单。
     */
    void insertPaymentOrder(@Param("paymentOrderId") String paymentOrderId,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("channelCode") String channelCode);

    /**
     * 新增预付单。
     */
    void insertPrepayOrder(@Param("prepayOrderNo") String prepayOrderNo,
            @Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("payScene") String payScene,
            @Param("cashierTitle") String cashierTitle,
            @Param("paymentOrderId") String paymentOrderId);

    /**
     * 查询预付单。
     */
    PrepayOrderDTO findPrepay(@Param("prepayOrderNo") String prepayOrderNo);

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
     * 更新支付单状态。
     */
    void updatePaymentStatus(@Param("paymentOrderId") String paymentOrderId,
            @Param("status") String status,
            @Param("statusType") String statusType,
            @Param("channelTransactionNo") String channelTransactionNo);

    /**
     * 更新预付单为支付中状态。
     */
    void updatePrepayToPaying(@Param("prepayOrderNo") String prepayOrderNo);

    /**
     * 按支付单更新收银台状态。
     */
    void updatePrepayStatusByPaymentOrderId(@Param("paymentOrderId") String paymentOrderId,
            @Param("cashierStatus") String cashierStatus,
            @Param("cashierStatusType") String cashierStatusType);

    /**
     * 回写支付方式和渠道编码。
     */
    void updatePaymentMethodAndChannel(@Param("paymentOrderId") String paymentOrderId,
            @Param("paymentMethod") String paymentMethod,
            @Param("channelCode") String channelCode);

    /**
     * 按支付单回写支付尝试状态，便于前后端查看当前尝试收口结果。
     */
    void updatePaymentAttemptStatusByPaymentOrderId(@Param("paymentOrderId") String paymentOrderId,
            @Param("attemptStatus") String attemptStatus,
            @Param("attemptStatusType") String attemptStatusType);

    /**
     * 支付成功后更新订单已支付金额和订单状态。
     */
    void updateOrderAfterPayment(@Param("orderNo") String orderNo,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("orderStatus") String orderStatus,
            @Param("orderStatusType") String orderStatusType);

    /**
     * 支付成功后更新账单已支付金额和账单状态。
     */
    void updateBillAfterPayment(@Param("orderNo") String orderNo,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("billStatus") String billStatus,
            @Param("billStatusType") String billStatusType);

    /**
     * 写入支付回调日志。
     */
    void insertNotifyLog(@Param("notifyNo") String notifyNo,
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
    void insertRouteRecord(@Param("routeNo") String routeNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("routeRule") String routeRule,
            @Param("routeResult") String routeResult);

    /**
     * 写入支付事件。
     */
    void insertEvent(@Param("eventNo") String eventNo,
            @Param("eventType") String eventType,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("bizNo") String bizNo,
            @Param("eventPayload") String eventPayload);

    /**
     * 写入支付尝试记录。
     */
    void insertPaymentAttempt(@Param("attemptNo") String attemptNo,
            @Param("prepayOrderNo") String prepayOrderNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("paymentMethod") String paymentMethod,
            @Param("terminal") String terminal,
            @Param("clientIp") String clientIp,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("requestPayload") String requestPayload,
            @Param("responsePayload") String responsePayload,
            @Param("attemptStatus") String attemptStatus,
            @Param("attemptStatusType") String attemptStatusType);

    /**
     * 判断幂等键对应的支付尝试是否已经存在。
     */
    boolean existsPaymentAttemptByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
}
