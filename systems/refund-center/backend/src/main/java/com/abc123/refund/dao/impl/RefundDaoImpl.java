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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * MyBatis 数据访问编排实现。
 */
@Repository
public class RefundDaoImpl implements RefundDao {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final RefundMapper refundMapper;

    public RefundDaoImpl(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    @Override
    public List<RefundListItemDTO> findList(RefundQueryDTO query) {
        return safeList(refundMapper.findList(query));
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
        return safeList(refundMapper.findLogs(refundOrderId));
    }

    @Override
    public PaymentSuccessProjectionEntity findPaymentSource(String paymentOrderId) {
        return refundMapper.findPaymentSource(paymentOrderId);
    }

    @Override
    public PaymentSuccessProjectionEntity findPaymentSourceForUpdate(String paymentOrderId) {
        return refundMapper.findPaymentSourceForUpdate(paymentOrderId);
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
    public int insertSuccessOutbox(String refundOrderId, String paymentOrderId, String payScene,
                                   BigDecimal refundAmount) {
        ObjectNode payload = JSON_MAPPER.createObjectNode();
        payload.put("refundOrderId", refundOrderId);
        payload.put("paymentOrderId", paymentOrderId);
        payload.put("payScene", payScene == null ? "" : payScene);
        payload.put("refundAmount", refundAmount.toPlainString());
        return refundMapper.insertSuccessOutbox(refundOrderId, payload.toString());
    }

    @Override
    public RefundOverviewDTO overview() {
        return refundMapper.overview();
    }

    @Override
    public List<RefundOutboxItemDTO> findOutboxList(RefundOutboxQueryDTO query) {
        return safeList(refundMapper.findOutboxList(query));
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
    public int markOutboxDispatching(String eventId) {
        return refundMapper.markOutboxDispatching(eventId);
    }

    @Override
    public int markOutboxSent(String eventId) {
        return refundMapper.markOutboxSent(eventId);
    }

    @Override
    public int markOutboxFailed(String eventId, String lastErrorMessage) {
        return refundMapper.markOutboxFailed(eventId, lastErrorMessage);
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }
}
