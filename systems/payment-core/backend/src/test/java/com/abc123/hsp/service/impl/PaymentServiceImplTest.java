package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付状态收口规则单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private PaymentCallbackSignatureService paymentCallbackSignatureService;

    @Mock
    private PaymentChannelRoutingService paymentChannelRoutingService;

    @Mock
    private PaymentChannelQueryService paymentChannelQueryService;

    @Test
    void shouldIgnoreLateCallbackWhenPaymentAlreadySucceeded() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-001");
        detail.setStatus("SUCCESS");
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-001")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-001");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("CHANNEL-002");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .callback("wx_h5", callback);

        verify(paymentMapper, never()).updatePaymentStatus(
                "PAY-001", "SUCCESS", "success", "CHANNEL-002");
        verify(paymentMapper, never()).insertNotifyLog(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAY-001"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectCallbackWithoutTradeStatusOrChannelTransactionNo() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-002");
        detail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-002")).thenReturn(detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-002");

        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService)
                        .callback("wx_h5", callback)
        );
    }

    @Test
    void shouldReuseActivePrepayWhenOrderIsStillOpen() {
        PrepayOrderDTO activePrepay = new PrepayOrderDTO();
        activePrepay.setPrepayOrderNo("PRE-001");
        activePrepay.setPaymentOrderId("PAY-001");
        activePrepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-001")).thenReturn(activePrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-001");
        request.setPayScene("HOME_CLEAN");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .prepay(request);

        verify(paymentMapper, times(1)).findLatestActivePrepayByOrderNo("ORD-001");
        verify(paymentMapper, never()).findOrderAmount("ORD-001");
        verify(paymentMapper, never()).insertPaymentOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).insertPrepayOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-001", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-001", result.getPaymentOrderId());
    }

    @Test
    void shouldCreateNewPrepayWhenNoActivePrepayExists() {
        PrepayOrderDTO createdPrepay = new PrepayOrderDTO();
        createdPrepay.setPrepayOrderNo("PRE-NEW");
        createdPrepay.setPaymentOrderId("PAY-NEW");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-002")).thenReturn(null);
        when(paymentMapper.findOrderAmount("ORD-002")).thenReturn(new java.math.BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-002")).thenReturn(java.math.BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-002")).thenReturn("张女士");
        when(paymentMapper.findBillNoByOrderNo("ORD-002")).thenReturn("BILL-002");
        when(paymentMapper.findPrepay(org.mockito.ArgumentMatchers.anyString())).thenReturn(createdPrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-002");
        request.setPayScene("HOME_CLEAN");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .prepay(request);

        verify(paymentMapper, times(1)).findLatestActivePrepayByOrderNo("ORD-002");
        verify(paymentMapper, times(1)).insertPaymentOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("ORD-002"),
                org.mockito.ArgumentMatchers.eq("张女士"),
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("168.00")),
                org.mockito.ArgumentMatchers.eq("HOME_CLEAN"));
        verify(paymentMapper, times(1)).insertPrepayOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("BILL-002"),
                org.mockito.ArgumentMatchers.eq("ORD-002"),
                org.mockito.ArgumentMatchers.eq("张女士"),
                org.mockito.ArgumentMatchers.eq(new java.math.BigDecimal("168.00")),
                org.mockito.ArgumentMatchers.eq("HOME_CLEAN"),
                org.mockito.ArgumentMatchers.eq("家政服务收银台"),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-NEW", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-NEW", result.getPaymentOrderId());
    }

    @Test
    void shouldReuseCurrentPrepayWhenSubmitAlreadyEnteredWaitCallback() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-100");
        prepay.setPaymentOrderId("PAY-100");
        when(paymentMapper.findPrepay("PRE-100")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-100");
        detail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-100")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-100");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .submit(request);

        verify(paymentMapper, times(1)).findPrepay("PRE-100");
        verify(paymentMapper, times(1)).findDetail("PAY-100");
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-100");
        verify(paymentMapper, never()).insertRouteRecord(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).insertPaymentAttempt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-100", result.getPrepayOrderNo());
    }

    @Test
    void shouldExposeQuerySourceWhenChannelAdapterReturnsResult() {
        PaymentChannelQueryAdapter adapter = new PaymentChannelQueryAdapter() {
            @Override
            public boolean supports(String channelCode) {
                return true;
            }

            @Override
            public PaymentChannelQueryResultDTO query(PaymentDetailDTO paymentDetail) {
                PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
                result.setTradeStatus("WAIT_CALLBACK");
                result.setChannelTransactionNo("CHANNEL-1001");
                result.setQuerySource("LOCAL_SIMULATION");
                return result;
            }
        };
        PaymentChannelQueryService queryService = new PaymentChannelQueryServiceImpl(Arrays.asList(adapter));

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-1001");
        detail.setChannel("wx_h5");

        PaymentDetailDTO result = queryService.query(detail);

        org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-1001", result.getChannelTransactionNo());
        org.junit.jupiter.api.Assertions.assertEquals("LOCAL_SIMULATION", result.getQuerySource());
    }
}
