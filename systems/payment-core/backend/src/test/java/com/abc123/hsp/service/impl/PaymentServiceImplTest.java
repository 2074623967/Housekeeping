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
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-100", result.getPrepayOrderNo());
    }

    @Test
    void shouldSkipDuplicateSubmitWhenIdempotencyKeyAlreadyExists() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-200");
        prepay.setPaymentOrderId("PAY-200");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-200")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-200");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-200")).thenReturn(detail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-200")).thenReturn(true);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-200");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setIdempotencyKey("IDEMP-200");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .submit(request);

        verify(paymentMapper, never()).updatePrepayToPaying("PRE-200");
        verify(paymentMapper, never()).insertPaymentAttempt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-200", result.getPrepayOrderNo());
    }

    @Test
    void shouldWriteConfiguredRoutingDecisionWhenSubmitPayment() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-300");
        prepay.setPaymentOrderId("PAY-300");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥6,800.00");
        prepay.setCustomerName("企业客户-晨星科技");
        when(paymentMapper.findPrepay("PRE-300")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-300");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-300")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("offline_bank");
        routeDecision.setRouteRule("RULE_ENTERPRISE_BANK");
        routeDecision.setRouteResult("企业大额订单走线下银行 -> offline_bank");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-300")).thenReturn(false);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-300")).thenReturn("ORD-300");
        when(paymentMapper.findPrepay("PRE-300")).thenReturn(prepay);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-300");
        request.setPaymentMethod("银行转账");
        request.setChannelCode("BANK_CARD");
        request.setTerminal("PC");
        request.setClientIp("10.0.0.3");
        request.setIdempotencyKey("IDEMP-300");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService)
                .submit(request);

        verify(paymentMapper, times(1)).updatePaymentMethodAndChannel("PAY-300", "银行转账", "offline_bank");
        verify(paymentMapper, times(1)).insertRouteRecord(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAY-300"),
                org.mockito.ArgumentMatchers.eq("offline_bank"),
                org.mockito.ArgumentMatchers.eq("RULE_ENTERPRISE_BANK"),
                org.mockito.ArgumentMatchers.eq("企业大额订单走线下银行 -> offline_bank"));
        verify(paymentMapper, times(1)).insertPaymentAttempt(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PRE-300"),
                org.mockito.ArgumentMatchers.eq("PAY-300"),
                org.mockito.ArgumentMatchers.eq("offline_bank"),
                org.mockito.ArgumentMatchers.eq("银行转账"),
                org.mockito.ArgumentMatchers.eq("PC"),
                org.mockito.ArgumentMatchers.eq("10.0.0.3"),
                org.mockito.ArgumentMatchers.eq("IDEMP-300"),
                org.mockito.ArgumentMatchers.contains("\"resolvedChannelCode\":\"offline_bank\""),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("处理中"),
                org.mockito.ArgumentMatchers.eq("info"));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-300", result.getPrepayOrderNo());
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
