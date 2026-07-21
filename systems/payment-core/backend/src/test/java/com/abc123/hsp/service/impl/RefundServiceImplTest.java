package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundOperationLogItemDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.mapper.RefundMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 退款单查询条件下推测试。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundMapper refundMapper;

    @Test
    void shouldForwardRefundQueryToMapper() {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId("REF-001");
        query.setPaymentOrderId("PAY-001");
        query.setRefundStatus("SUCCESS");
        query.setRefundMethod("原路退回");
        query.setPageNo(0);
        query.setPageSize(999);

        when(refundMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(refundMapper.count(query)).thenReturn(0L);
        new RefundServiceImpl(refundMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals(1, query.getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(100, query.getPageSize());
        verify(refundMapper).findAll(query);
        verify(refundMapper).count(query);
    }

    @Test
    void shouldApplyRefundForSuccessfulPayment() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setRefundAmount(new BigDecimal("20.00"));
        request.setRefundMethod("原路退款");

        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setOrderNo("ORD-001");
        source.setCustomerName("张女士");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSource("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(new BigDecimal("30.00"));

        new RefundServiceImpl(refundMapper).apply(request);

        verify(refundMapper).insertRefund(
                org.mockito.ArgumentMatchers.startsWith("REF"),
                org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq("REVIEWING"),
                org.mockito.ArgumentMatchers.eq("warn")
        );
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRefundWhenAmountExceedsPaidAmount() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setRefundAmount(new BigDecimal("80.00"));
        request.setRefundMethod("原路退款");

        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSource("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(new BigDecimal("30.00"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RefundServiceImpl(refundMapper).apply(request)
        );
    }

    @Test
    void shouldApproveReviewingRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-001");
        when(refundMapper.updateRefundStatus("REF-001", "REVIEWING", "PROCESSING", "warn", false)).thenReturn(1);

        new RefundServiceImpl(refundMapper).approve(request);

        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.any());
        verify(refundMapper).findByRefundOrderId("REF-001");
    }

    @Test
    void shouldLoadRefundDetailWithOperationLogs() {
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId("REF-001");
        RefundOperationLogItemDTO logItem = new RefundOperationLogItemDTO();
        logItem.setLogNo("ROL-001");
        when(refundMapper.findDetailByRefundOrderId("REF-001")).thenReturn(detail);
        when(refundMapper.findOperationLogs("REF-001")).thenReturn(Arrays.asList(logItem));

        RefundDetailDTO result = new RefundServiceImpl(refundMapper).detail("REF-001");

        org.junit.jupiter.api.Assertions.assertEquals("REF-001", result.getRefundOrderId());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getOperationLogs().size());
        verify(refundMapper).findDetailByRefundOrderId("REF-001");
        verify(refundMapper).findOperationLogs("REF-001");
    }

    @Test
    void shouldRejectInvalidRefundStatusTransition() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-001");
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "SUCCESS", "success", true)).thenReturn(0);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new RefundServiceImpl(refundMapper).markSuccess(request)
        );
    }
}
