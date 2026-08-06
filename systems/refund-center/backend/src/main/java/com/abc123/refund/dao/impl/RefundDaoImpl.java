package com.abc123.refund.dao.impl;

import com.abc123.refund.dao.RefundDao;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundOverviewDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOrderEntity;
import com.abc123.refund.entity.RefundOutboxEventEntity;
import com.abc123.refund.mapper.RefundMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * MyBatis 数据访问编排实现。
 */
@Repository
public class RefundDaoImpl implements RefundDao {

    private final RefundMapper refundMapper;

    public RefundDaoImpl(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    @Override
    public List<RefundListItemDTO> findList(RefundQueryDTO query) {
        return refundMapper.findList(query);
    }

    @Override
    public long count(RefundQueryDTO query) {
        return refundMapper.count(query);
    }

    @Override
    public RefundListItemDTO findByRefundOrderId(String refundOrderId) {
        return refundMapper.findByRefundOrderId(refundOrderId);
    }

    @Override
    public RefundListItemDTO findByIdempotencyKey(String idempotencyKey) {
        return refundMapper.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<RefundOperationLogDTO> findLogs(String refundOrderId) {
        return refundMapper.findLogs(refundOrderId);
    }

    @Override
    public PaymentSuccessProjectionEntity findPaymentSource(String paymentOrderId) {
        return refundMapper.findPaymentSource(paymentOrderId);
    }

    @Override
    public BigDecimal sumActiveRefundAmount(String paymentOrderId) {
        return refundMapper.sumActiveRefundAmount(paymentOrderId);
    }

    @Override
    public int insertPaymentSource(PaymentSuccessProjectionEntity source) {
        return refundMapper.insertPaymentSource(source);
    }

    @Override
    public int insertRefund(RefundOrderEntity refund) {
        return refundMapper.insertRefund(refund);
    }

    @Override
    public int updateStatus(String refundOrderId, String expectedStatus, String targetStatus, String failureCode) {
        return refundMapper.updateStatus(refundOrderId, expectedStatus, targetStatus, failureCode);
    }

    @Override
    public int updateCallback(String refundOrderId, String targetStatus, String channelRefundId, String failureCode) {
        return refundMapper.updateCallback(refundOrderId, targetStatus, channelRefundId, failureCode);
    }

    @Override
    public int insertLog(String refundOrderId, String actionCode, String actionName,
                          String fromStatus, String toStatus, String operatorName, String remark) {
        return refundMapper.insertLog(refundOrderId, actionCode, actionName,
                fromStatus, toStatus, operatorName, remark);
    }

    @Override
    public int insertSuccessOutbox(String refundOrderId, String paymentOrderId, BigDecimal refundAmount) {
        return refundMapper.insertSuccessOutbox(refundOrderId, paymentOrderId, refundAmount);
    }

    @Override
    public RefundOverviewDTO overview() {
        return refundMapper.overview();
    }

    @Override
    public List<RefundOutboxItemDTO> findOutboxList(RefundOutboxQueryDTO query) {
        return refundMapper.findOutboxList(query);
    }

    @Override
    public long countOutbox(RefundOutboxQueryDTO query) {
        return refundMapper.countOutbox(query);
    }

    @Override
    public RefundOutboxEventEntity findOutboxByEventId(String eventId) {
        return refundMapper.findOutboxByEventId(eventId);
    }

    @Override
    public int markOutboxSent(String eventId) {
        return refundMapper.markOutboxSent(eventId);
    }

    @Override
    public int markOutboxFailed(String eventId, String lastErrorMessage) {
        return refundMapper.markOutboxFailed(eventId, lastErrorMessage);
    }
}
