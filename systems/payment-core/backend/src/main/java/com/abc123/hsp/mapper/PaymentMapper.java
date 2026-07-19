package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PaymentMapper {

    List<PaymentListItemDTO> findAll();

    PaymentDetailDTO findDetail(@Param("paymentOrderId") String paymentOrderId);

    BigDecimal findOrderAmount(@Param("orderNo") String orderNo);

    BigDecimal findPaidAmount(@Param("orderNo") String orderNo);

    String findCustomerNameByOrderNo(@Param("orderNo") String orderNo);

    String findBillNoByOrderNo(@Param("orderNo") String orderNo);

    void insertBill(@Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("orderAmount") BigDecimal orderAmount,
            @Param("paidAmount") BigDecimal paidAmount,
            @Param("billStatus") String billStatus,
            @Param("billStatusType") String billStatusType);

    String findLatestPaymentOrderIdByOrderNo(@Param("orderNo") String orderNo);

    void insertPaymentOrder(@Param("paymentOrderId") String paymentOrderId,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("channelCode") String channelCode);

    void insertPrepayOrder(@Param("prepayOrderNo") String prepayOrderNo,
            @Param("billNo") String billNo,
            @Param("orderNo") String orderNo,
            @Param("customerName") String customerName,
            @Param("amount") BigDecimal amount,
            @Param("payScene") String payScene,
            @Param("cashierTitle") String cashierTitle,
            @Param("paymentOrderId") String paymentOrderId);

    PrepayOrderDTO findPrepay(@Param("prepayOrderNo") String prepayOrderNo);

    List<String> findNotifyLogs(@Param("paymentOrderId") String paymentOrderId);

    List<String> findRouteLogs(@Param("paymentOrderId") String paymentOrderId);

    List<String> findEventItems(@Param("paymentOrderId") String paymentOrderId);

    String findPaymentOrderIdByPrepayOrderNo(@Param("prepayOrderNo") String prepayOrderNo);

    String findOrderNoByPrepayOrderNo(@Param("prepayOrderNo") String prepayOrderNo);

    void updatePaymentStatus(@Param("paymentOrderId") String paymentOrderId,
            @Param("status") String status,
            @Param("statusType") String statusType,
            @Param("channelTransactionNo") String channelTransactionNo);

    void updatePrepayToPaying(@Param("prepayOrderNo") String prepayOrderNo);

    void updatePaymentMethodAndChannel(@Param("paymentOrderId") String paymentOrderId,
            @Param("paymentMethod") String paymentMethod,
            @Param("channelCode") String channelCode);

    void insertNotifyLog(@Param("notifyNo") String notifyNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("notifyType") String notifyType,
            @Param("notifyPayload") String notifyPayload,
            @Param("notifyResult") String notifyResult,
            @Param("notifyStatus") String notifyStatus,
            @Param("notifyStatusType") String notifyStatusType);

    void insertRouteRecord(@Param("routeNo") String routeNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("routeRule") String routeRule,
            @Param("routeResult") String routeResult);

    void insertEvent(@Param("eventNo") String eventNo,
            @Param("eventType") String eventType,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("bizNo") String bizNo,
            @Param("eventPayload") String eventPayload);

    void insertPaymentAttempt(@Param("attemptNo") String attemptNo,
            @Param("prepayOrderNo") String prepayOrderNo,
            @Param("paymentOrderId") String paymentOrderId,
            @Param("channelCode") String channelCode,
            @Param("paymentMethod") String paymentMethod,
            @Param("requestPayload") String requestPayload,
            @Param("responsePayload") String responsePayload,
            @Param("attemptStatus") String attemptStatus,
            @Param("attemptStatusType") String attemptStatusType);
}
