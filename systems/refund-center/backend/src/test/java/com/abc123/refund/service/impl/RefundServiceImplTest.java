package com.abc123.refund.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.dao.RefundDao;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOutboxEventEntity;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 退款状态机和金额边界单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundDao refundDao;

    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(refundDao, "test-operator");
    }

    @Test
    void shouldRejectRefundWhenPaymentSourceMissing() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));
        when(refundDao.findPaymentSource("PAY-1")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.apply(request));
    }

    @Test
    void shouldRejectRefundWhenAmountExceedsPaidAmount() {
        PaymentSuccessProjectionEntity source = source("PAY-1", "100.00");
        when(refundDao.findPaymentSource("PAY-1")).thenReturn(source);
        when(refundDao.sumActiveRefundAmount("PAY-1")).thenReturn(new BigDecimal("95.00"));
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));

        assertThrows(BusinessException.class, () -> service.apply(request));
    }

    @Test
    void shouldCreateReviewingRefund() {
        PaymentSuccessProjectionEntity source = source("PAY-1", "100.00");
        when(refundDao.findPaymentSource("PAY-1")).thenReturn(source);
        when(refundDao.sumActiveRefundAmount("PAY-1")).thenReturn(BigDecimal.ZERO);
        when(refundDao.findByIdempotencyKey(any())).thenReturn(null);
        when(refundDao.insertRefund(any())).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("REVIEWING");
        when(refundDao.findByRefundOrderId(any())).thenReturn(result);
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));

        assertEquals("REVIEWING", service.apply(request).getStatus());
        verify(refundDao).insertLog(any(), eq("APPLY"), eq("发起退款申请"),
                eq("INIT"), eq("REVIEWING"), eq("test-operator"), any());
    }

    @Test
    void shouldMoveReviewingToApproved() {
        when(refundDao.updateStatus("REF-1", "REVIEWING", "APPROVED", null)).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("APPROVED");
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-1");

        assertEquals("APPROVED", service.approve(request).getStatus());
    }

    @Test
    void shouldRejectInvalidTransition() {
        when(refundDao.updateStatus("REF-1", "APPROVED", "PROCESSING", null)).thenReturn(0);
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-1");

        assertThrows(BusinessException.class, () -> service.submit(request));
    }

    @Test
    void shouldHandleSuccessCallback() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-1", null)).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("SUCCESS");
        result.setPaymentOrderId("PAY-1");
        result.setRefundAmount(new BigDecimal("10.00"));
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1");

        assertEquals("SUCCESS", service.callback(request).getStatus());
        verify(refundDao).insertSuccessOutbox("REF-1", "PAY-1", new BigDecimal("10.00"));
    }

    @Test
    void shouldReturnExistingRefundWhenSuccessCallbackRepeated() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-1", null)).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("SUCCESS");
        result.setRefundOrderId("REF-1");
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1");

        assertEquals("SUCCESS", service.callback(request).getStatus());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any());
    }

    @Test
    void shouldReturnExistingRefundWhenFailCallbackRepeated() {
        when(refundDao.updateCallback("REF-2", "FAIL", "CH-2", "E001")).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("FAIL");
        result.setRefundOrderId("REF-2");
        when(refundDao.findByRefundOrderId("REF-2")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-2");
        request.setResult("FAIL");
        request.setChannelRefundId("CH-2");
        request.setFailureCode("E001");

        assertEquals("FAIL", service.callback(request).getStatus());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldProjectPaymentSuccessIdempotently() {
        PaymentSuccessProjectionDTO request = new PaymentSuccessProjectionDTO();
        request.setPaymentOrderId("PAY-2");
        request.setOrderNo("ORDER-2");
        request.setPaidAmount(new BigDecimal("20.00"));

        service.projectPaymentSuccess(request);

        verify(refundDao).insertPaymentSource(any(PaymentSuccessProjectionEntity.class));
    }

    @Test
    void shouldDispatchOutboxSuccessfully() {
        RefundOutboxEventEntity event = new RefundOutboxEventEntity();
        event.setEventId("REVT-1");
        event.setStatus("PENDING");
        when(refundDao.findOutboxByEventId("REVT-1")).thenReturn(event);
        when(refundDao.markOutboxSent("REVT-1")).thenReturn(1);
        RefundOutboxItemDTO item = new RefundOutboxItemDTO();
        item.setEventId("REVT-1");
        item.setStatus("SENT");
        when(refundDao.findOutboxList(any())).thenReturn(Collections.singletonList(item));

        RefundOutboxDispatchRequestDTO request = new RefundOutboxDispatchRequestDTO();
        request.setSimulateResult("SUCCESS");

        assertEquals("SENT", service.dispatchOutbox("REVT-1", request).getStatus());
        verify(refundDao).markOutboxSent("REVT-1");
    }

    @Test
    void shouldMarkOutboxFailedWhenDispatchFails() {
        RefundOutboxEventEntity event = new RefundOutboxEventEntity();
        event.setEventId("REVT-2");
        event.setStatus("PENDING");
        when(refundDao.findOutboxByEventId("REVT-2")).thenReturn(event);
        when(refundDao.markOutboxFailed("REVT-2", "账务系统暂不可用")).thenReturn(1);
        RefundOutboxItemDTO item = new RefundOutboxItemDTO();
        item.setEventId("REVT-2");
        item.setStatus("FAIL");
        item.setRetryCount(1);
        when(refundDao.findOutboxList(any())).thenReturn(Collections.singletonList(item));

        RefundOutboxDispatchRequestDTO request = new RefundOutboxDispatchRequestDTO();
        request.setSimulateResult("FAIL");
        request.setRemark("账务系统暂不可用");

        assertEquals("FAIL", service.dispatchOutbox("REVT-2", request).getStatus());
        verify(refundDao).markOutboxFailed("REVT-2", "账务系统暂不可用");
    }

    private PaymentSuccessProjectionEntity source(String paymentOrderId, String amount) {
        PaymentSuccessProjectionEntity source = new PaymentSuccessProjectionEntity();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-1");
        source.setCustomerName("客户");
        source.setPaidAmount(new BigDecimal(amount));
        return source;
    }
}
