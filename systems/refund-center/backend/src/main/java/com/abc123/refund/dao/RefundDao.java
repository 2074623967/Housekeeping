package com.abc123.refund.dao;

import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.entity.RefundOrderEntity;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import java.math.BigDecimal;
import java.util.List;

/**
 * 退款中心数据访问编排层。
 */
public interface RefundDao {

    List<RefundListItemDTO> findList(RefundQueryDTO query);

    long count(RefundQueryDTO query);

    RefundListItemDTO findByRefundOrderId(String refundOrderId);

    RefundListItemDTO findByIdempotencyKey(String idempotencyKey);

    List<RefundOperationLogDTO> findLogs(String refundOrderId);

    PaymentSuccessProjectionEntity findPaymentSource(String paymentOrderId);

    BigDecimal sumActiveRefundAmount(String paymentOrderId);

    int insertPaymentSource(PaymentSuccessProjectionEntity source);

    int insertRefund(RefundOrderEntity refund);

    int updateStatus(String refundOrderId, String expectedStatus, String targetStatus, String failureCode);

    int updateCallback(String refundOrderId, String targetStatus, String channelRefundId, String failureCode);

    int insertLog(String refundOrderId, String actionCode, String actionName,
                  String fromStatus, String toStatus, String operatorName, String remark);

    int insertSuccessOutbox(String refundOrderId, String paymentOrderId, BigDecimal refundAmount);

    RefundOverviewDTO overview();
}
