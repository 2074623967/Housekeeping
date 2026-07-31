package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundOperationLogItemDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundChannelSubmitService;
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

    @Mock
    private RefundChannelSubmitService refundChannelSubmitService;

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
        new RefundServiceImpl(refundMapper, refundChannelSubmitService).list(query);

        assertEquals(1, query.getPageNo());
        assertEquals(100, query.getPageSize());
        verify(refundMapper).findAll(query);
        verify(refundMapper).count(query);
    }

    @Test
    void shouldExportNormalizedRefundsAsCsv() {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId(" REF-001 ");
        query.setPaymentOrderId(" PAY-001 ");
        query.setRefundStatus(" SUCCESS ");
        query.setRefundMethod(" 原路退款 ");
        RefundListItemDTO item = new RefundListItemDTO();
        item.setRefundOrderId("REF-001");
        item.setCustomerName("张\"女士");
        item.setStatusType("success");
        when(refundMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new RefundServiceImpl(refundMapper, refundChannelSubmitService).exportCsv(query);

        assertEquals("REF-001", query.getRefundOrderId());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("SUCCESS", query.getRefundStatus());
        assertEquals("原路退款", query.getRefundMethod());
        assertTrue(csv.startsWith("\uFEFF退款单号,支付单号,订单号,客户名称"));
        assertTrue(csv.contains("\"success\""));
        assertTrue(csv.contains("\"张\"\"女士\""));
        verify(refundMapper).findAllForExport(query);
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

        new RefundServiceImpl(refundMapper, refundChannelSubmitService).apply(request);

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
                () -> new RefundServiceImpl(refundMapper, refundChannelSubmitService).apply(request)
        );
    }

    @Test
    void shouldApproveReviewingRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-001");
        when(refundMapper.updateRefundStatus("REF-001", "REVIEWING", "PROCESSING", "warn", false)).thenReturn(1);
        RefundChannelSubmitRequestDTO submitRequest = new RefundChannelSubmitRequestDTO();
        submitRequest.setRefundOrderId("REF-001");
        submitRequest.setPaymentOrderId("PAY-001");
        submitRequest.setOrderNo("ORD-001");
        submitRequest.setCustomerName("张女士");
        submitRequest.setRefundAmount(new BigDecimal("20.00"));
        submitRequest.setRefundMethod("原路退款");
        submitRequest.setRefundReason("客户取消服务");
        submitRequest.setChannelCode("wx_h5");
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(submitRequest);
        RefundChannelSubmitResultDTO submitResult = new RefundChannelSubmitResultDTO();
        submitResult.setChannelRefundNo("RCH-001");
        submitResult.setStatus("PROCESSING");
        submitResult.setStatusType("warn");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\"}");
        when(refundChannelSubmitService.submit(submitRequest)).thenReturn(submitResult);

        new RefundServiceImpl(refundMapper, refundChannelSubmitService).approve(request);

        verify(refundChannelSubmitService).submit(submitRequest);
        verify(refundMapper, times(2)).insertOperationLog(org.mockito.ArgumentMatchers.any());
        verify(refundMapper).findByRefundOrderId("REF-001");
    }

    @Test
    void shouldResubmitChannelWhenRetryFailedRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-002");
        when(refundMapper.updateRefundStatus("REF-002", "FAIL", "PROCESSING", "warn", false)).thenReturn(1);
        RefundChannelSubmitRequestDTO submitRequest = new RefundChannelSubmitRequestDTO();
        submitRequest.setRefundOrderId("REF-002");
        submitRequest.setPaymentOrderId("PAY-002");
        submitRequest.setOrderNo("ORD-002");
        submitRequest.setCustomerName("王先生");
        submitRequest.setRefundAmount(new BigDecimal("80.00"));
        submitRequest.setRefundMethod("原路退款");
        submitRequest.setRefundReason("服务取消");
        submitRequest.setChannelCode("alipay_h5");
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-002")).thenReturn(submitRequest);
        RefundChannelSubmitResultDTO submitResult = new RefundChannelSubmitResultDTO();
        submitResult.setChannelRefundNo("RCH-002");
        submitResult.setStatus("PROCESSING");
        submitResult.setStatusType("warn");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\"}");
        when(refundChannelSubmitService.submit(submitRequest)).thenReturn(submitResult);

        new RefundServiceImpl(refundMapper, refundChannelSubmitService).retry(request);

        verify(refundChannelSubmitService).submit(submitRequest);
        verify(refundMapper).findByRefundOrderId("REF-002");
    }

    @Test
    void shouldLoadRefundDetailWithOperationLogs() {
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId("REF-001");
        RefundOperationLogItemDTO logItem = new RefundOperationLogItemDTO();
        logItem.setLogNo("ROL-001");
        when(refundMapper.findDetailByRefundOrderId("REF-001")).thenReturn(detail);
        when(refundMapper.findOperationLogs("REF-001")).thenReturn(Arrays.asList(logItem));

        RefundDetailDTO result = new RefundServiceImpl(refundMapper, refundChannelSubmitService).detail("REF-001");

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
                () -> new RefundServiceImpl(refundMapper, refundChannelSubmitService).markSuccess(request)
        );
    }
}
