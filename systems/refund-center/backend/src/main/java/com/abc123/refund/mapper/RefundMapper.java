package com.abc123.refund.mapper;

import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOrderEntity;
import com.abc123.refund.entity.RefundOutboxEventEntity;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 退款中心 MyBatis Mapper。
 */
@Mapper
public interface RefundMapper {

    List<RefundListItemDTO> findList(RefundQueryDTO query);

    long count(RefundQueryDTO query);

    RefundListItemDTO findByRefundOrderId(@Param("refundOrderId") String refundOrderId);

    RefundListItemDTO findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    List<RefundOperationLogDTO> findLogs(@Param("refundOrderId") String refundOrderId);

    PaymentSuccessProjectionEntity findPaymentSource(@Param("paymentOrderId") String paymentOrderId);

    BigDecimal sumActiveRefundAmount(@Param("paymentOrderId") String paymentOrderId);

    int insertPaymentSource(PaymentSuccessProjectionEntity source);

    int insertRefund(RefundOrderEntity refund);

    int updateStatus(@Param("refundOrderId") String refundOrderId,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("targetStatus") String targetStatus,
                     @Param("failureCode") String failureCode);

    int updateCallback(@Param("refundOrderId") String refundOrderId,
                       @Param("targetStatus") String targetStatus,
                       @Param("channelRefundId") String channelRefundId,
                       @Param("failureCode") String failureCode);

    int insertLog(@Param("refundOrderId") String refundOrderId,
                  @Param("actionCode") String actionCode,
                  @Param("actionName") String actionName,
                  @Param("fromStatus") String fromStatus,
                  @Param("toStatus") String toStatus,
                  @Param("operatorName") String operatorName,
                  @Param("remark") String remark);

    int insertSuccessOutbox(@Param("refundOrderId") String refundOrderId,
                            @Param("paymentOrderId") String paymentOrderId,
                            @Param("refundAmount") BigDecimal refundAmount);

    RefundOverviewDTO overview();

    List<RefundOutboxItemDTO> findOutboxList(RefundOutboxQueryDTO query);

    long countOutbox(RefundOutboxQueryDTO query);

    RefundOutboxEventEntity findOutboxByEventId(@Param("eventId") String eventId);

    int markOutboxSent(@Param("eventId") String eventId);

    int markOutboxFailed(@Param("eventId") String eventId,
                         @Param("lastErrorMessage") String lastErrorMessage);
}
