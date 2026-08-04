package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitConcurrencyTokenDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentOpsConfigService;
import com.abc123.hsp.service.PaymentRiskControlService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import java.math.BigDecimal;
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

    @Mock
    private PaymentChannelSubmitService paymentChannelSubmitService;

    @Mock
    private PaymentEventDispatchService paymentEventDispatchService;

    @Mock
    private PaymentRiskControlService paymentRiskControlService;

    @Mock
    private PaymentOpsConfigService paymentOpsConfigService;

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
                paymentChannelQueryService,
                paymentChannelSubmitService)
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
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback)
        );
    }

    @Test
    void shouldThrowBusinessExceptionWhenCashierPrepayOrderMissing() {
        when(paymentMapper.findPrepay("PRE-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .cashier("PRE-MISSING")
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, exception.getCode());
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
                paymentChannelQueryService,
                paymentChannelSubmitService)
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
                paymentChannelQueryService,
                paymentChannelSubmitService)
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
    void shouldCreateVirtualPrepayWhenOrderSourceMissing() {
        PrepayOrderDTO createdPrepay = new PrepayOrderDTO();
        createdPrepay.setPrepayOrderNo("PRE-VIRTUAL");
        createdPrepay.setPaymentOrderId("PAY-VIRTUAL");
        when(paymentMapper.findLatestActivePrepayByOrderNo("WALLET-RECHARGE-001")).thenReturn(null);
        when(paymentMapper.findOrderAmount("WALLET-RECHARGE-001")).thenReturn(null);
        when(paymentMapper.findPaidAmount("WALLET-RECHARGE-001")).thenReturn(null);
        when(paymentMapper.findPrepay(org.mockito.ArgumentMatchers.anyString())).thenReturn(createdPrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-RECHARGE-001");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("99.00"));
        request.setCashierTitle("钱包充值收银台");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .prepay(request);

        verify(paymentMapper, times(1)).insertBill(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("WALLET-RECHARGE-001"),
                org.mockito.ArgumentMatchers.eq("张女士"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("99.00")),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.eq("待支付"),
                org.mockito.ArgumentMatchers.eq("warn"));
        verify(paymentMapper, times(1)).insertPaymentOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("WALLET-RECHARGE-001"),
                org.mockito.ArgumentMatchers.eq("张女士"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("99.00")),
                org.mockito.ArgumentMatchers.eq("WALLET_RECHARGE_APP"));
        verify(paymentMapper, times(1)).insertPrepayOrder(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("WALLET-RECHARGE-001"),
                org.mockito.ArgumentMatchers.eq("张女士"),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("99.00")),
                org.mockito.ArgumentMatchers.eq("WALLET_RECHARGE_APP"),
                org.mockito.ArgumentMatchers.eq("钱包充值收银台"),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-VIRTUAL", result.getPrepayOrderNo());
    }

    @Test
    void shouldUsePaymentAmountToCloseBillWhenCallbackOrderSourceMissing() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-VIRTUAL-CALLBACK");
        detail.setOrderNo("WALLET-RECHARGE-001");
        detail.setStatus("WAIT_CALLBACK");
        detail.setAmount("¥99.00");
        when(paymentMapper.findDetail("PAY-VIRTUAL-CALLBACK")).thenReturn(detail, detail);
        when(paymentMapper.findOrderAmount("WALLET-RECHARGE-001")).thenReturn(null);
        when(paymentMapper.findRouteLogs("PAY-VIRTUAL-CALLBACK")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-VIRTUAL-CALLBACK")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-VIRTUAL-CALLBACK")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-VIRTUAL-CALLBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("ALI-VIRTUAL-001");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("alipay_h5", callback);

        verify(paymentMapper, never()).updateOrderAfterPayment(
                org.mockito.ArgumentMatchers.eq("WALLET-RECHARGE-001"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, times(1)).updateBillAfterPayment(
                "WALLET-RECHARGE-001",
                new BigDecimal("99.00"),
                "已结清",
                "success");
    }

    @Test
    void shouldPublishDownstreamEventWhenCallbackMarksPaymentSuccess() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-001");
        detail.setOrderNo("ORD-CALLBACK-001");
        detail.setStatus("WAIT_CALLBACK");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-001")).thenReturn(detail, detail);
        when(paymentMapper.findOrderAmount("ORD-CALLBACK-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-001");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-001");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback);

        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_SUCCESS"),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-001"),
                org.mockito.ArgumentMatchers.eq("ORD-CALLBACK-001"),
                org.mockito.ArgumentMatchers.eq("{\"channel\":\"wx_h5\"}")
        );
        verify(paymentEventDispatchService).publishPaymentSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-001")
        );
        verify(paymentMapper).releaseSubmitConcurrencyToken("PAY-CALLBACK-001", "CALLBACK_SUCCESS");
    }

    @Test
    void shouldNotPublishDownstreamEventWhenCallbackStillPending() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-002");
        detail.setOrderNo("ORD-CALLBACK-002");
        detail.setStatus("WAIT_CALLBACK");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-002")).thenReturn(detail, detail);
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-002");
        callback.setTradeStatus("PROCESSING");
        callback.setChannelTransactionNo("WX-CALLBACK-002");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback);

        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_PENDING"),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-002"),
                org.mockito.ArgumentMatchers.eq("ORD-CALLBACK-002"),
                org.mockito.ArgumentMatchers.eq("{\"channel\":\"wx_h5\"}")
        );
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken("PAY-CALLBACK-002", "CALLBACK_SUCCESS");
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
                paymentChannelQueryService,
                paymentChannelSubmitService)
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
                paymentChannelQueryService,
                paymentChannelSubmitService)
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-200", result.getPrepayOrderNo());
    }

    @Test
    void shouldReturnLatestPrepayWhenConcurrentSubmitAlreadyOccupiedCashier() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-250");
        prepay.setPaymentOrderId("PAY-250");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("李女士");
        when(paymentMapper.findPrepay("PRE-250")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-250");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-250")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-250")).thenReturn(false);
        when(paymentMapper.findSubmitConcurrencyToken("PRE-250", "default-app")).thenReturn(null);
        when(paymentMapper.updatePrepayToPaying("PRE-250")).thenReturn(0);

        PrepayOrderDTO latestPrepay = new PrepayOrderDTO();
        latestPrepay.setPrepayOrderNo("PRE-250");
        latestPrepay.setPaymentOrderId("PAY-250");
        latestPrepay.setCashierStatus("支付中");
        when(paymentMapper.findPrepay("PRE-250")).thenReturn(prepay, latestPrepay);
        when(paymentMapper.findDetail("PAY-250")).thenReturn(detail, detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-250");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setIdempotencyKey("IDEMP-250");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .submit(request);

        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals("支付中", result.getCashierStatus());
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
        when(paymentMapper.updatePrepayToPaying("PRE-300")).thenReturn(1);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-300")).thenReturn("ORD-300");
        when(paymentMapper.findPrepay("PRE-300")).thenReturn(prepay);
        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-pc-web");
        controlPolicy.setAllowedPaymentMethods("银行转账");
        controlPolicy.setAllowedChannelCodes("offline_bank");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_PC");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("开启");
        controlPolicy.setAccessTokenValue("token-housekeeping-pc-web");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-pc-web")).thenReturn(controlPolicy);
        when(paymentMapper.countRecentAttemptsBySourceAppAndMethod("housekeeping-pc-web", "银行转账")).thenReturn(0);
        when(paymentMapper.findSubmitConcurrencyToken("PRE-300", "housekeeping-pc-web")).thenReturn(null);
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("CHANNEL-300");
        submitResult.setAttemptStatus("处理中");
        submitResult.setAttemptStatusType("info");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\",\"channelTransactionNo\":\"CHANNEL-300\"}");
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-300");
        request.setPaymentMethod("银行转账");
        request.setChannelCode("BANK_CARD");
        request.setSourceAppId("housekeeping-pc-web");
        request.setMerchantNo("MCH_HOME_PC");
        request.setAccessToken("token-housekeeping-pc-web");
        request.setTerminal("PC");
        request.setClientIp("10.0.0.3");
        request.setIdempotencyKey("IDEMP-300");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .submit(request);

        verify(paymentMapper, times(1)).updatePaymentMethodAndChannel("PAY-300", "银行转账", "offline_bank", "CHANNEL-300");
        verify(paymentMapper, times(1)).insertSubmitConcurrencyToken(
                org.mockito.ArgumentMatchers.eq("PRE-300"),
                org.mockito.ArgumentMatchers.eq("PAY-300"),
                org.mockito.ArgumentMatchers.eq("housekeeping-pc-web"),
                org.mockito.ArgumentMatchers.eq("IDEMP-300"),
                org.mockito.ArgumentMatchers.eq("PC"),
                org.mockito.ArgumentMatchers.eq("10.0.0.3"),
                org.mockito.ArgumentMatchers.anyInt());
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
                org.mockito.ArgumentMatchers.eq("housekeeping-pc-web"),
                org.mockito.ArgumentMatchers.eq("PC"),
                org.mockito.ArgumentMatchers.eq("10.0.0.3"),
                org.mockito.ArgumentMatchers.eq("IDEMP-300"),
                org.mockito.ArgumentMatchers.argThat(payload ->
                        payload != null
                                && payload.contains("\"sourceAppId\":\"housekeeping-pc-web\"")
                                && payload.contains("\"resolvedChannelCode\":\"offline_bank\"")),
                org.mockito.ArgumentMatchers.eq("{\"code\":\"SUCCESS\",\"channelTransactionNo\":\"CHANNEL-300\"}"),
                org.mockito.ArgumentMatchers.eq("处理中"),
                org.mockito.ArgumentMatchers.eq("info"));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-300", result.getPrepayOrderNo());
    }

    @Test
    void shouldRejectSubmitWhenSourceAppPaymentMethodNotAllowed() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-310");
        prepay.setPaymentOrderId("PAY-310");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-310")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-310");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-310")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("alipay_h5");
        routeDecision.setRouteRule("RULE_HOME_ALI");
        routeDecision.setRouteResult("家政 H5 支付宝兜底 -> alipay_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付");
        controlPolicy.setAllowedChannelCodes("wx_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-310");
        request.setPaymentMethod("支付宝");
        request.setChannelCode("ALI_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_HOME_APP");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_SOURCE_APP_NOT_ALLOWED, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-310");
    }

    @Test
    void shouldRejectSubmitWhenMerchantNotAllowed() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-315");
        prepay.setPaymentOrderId("PAY-315");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-315")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-315");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-315")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付,支付宝");
        controlPolicy.setAllowedChannelCodes("wx_h5,alipay_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-315");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_BAD");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_MERCHANT_NOT_ALLOWED, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-315");
    }

    @Test
    void shouldRejectSubmitWhenAccessTokenInvalid() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-316");
        prepay.setPaymentOrderId("PAY-316");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-316")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-316");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-316")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付,支付宝");
        controlPolicy.setAllowedChannelCodes("wx_h5,alipay_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("开启");
        controlPolicy.setAccessTokenValue("token-housekeeping-app-web");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-316");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_HOME_APP");
        request.setAccessToken("bad-token");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-316");
    }

    @Test
    void shouldRejectSubmitWhenInterfaceMinuteRateLimitExceeded() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-318");
        prepay.setPaymentOrderId("PAY-318");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-318")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-318");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-318")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付");
        controlPolicy.setAllowedChannelCodes("wx_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setInterfaceMinuteSubmitLimit(2);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(controlPolicy);
        when(paymentMapper.countRecentAttemptsBySubmitScope("housekeeping-app-web", "H5", "10.0.0.20")).thenReturn(2);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-318");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_HOME_APP");
        request.setTerminal("H5");
        request.setClientIp("10.0.0.20");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-318");
    }

    @Test
    void shouldRejectSubmitWhenStrictSelfCheckNotPassed() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-320");
        prepay.setPaymentOrderId("PAY-320");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-320")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-320");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-320")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-h5-web");
        controlPolicy.setAllowedPaymentMethods("微信支付,支付宝");
        controlPolicy.setAllowedChannelCodes("wx_h5,alipay_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("开启");
        controlPolicy.setSelfCheckStatus("WARN");
        controlPolicy.setSelfCheckMessage("H5 收银台自检未通过，请先处理探活异常");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-h5-web")).thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-320");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-h5-web");
        request.setMerchantNo("MCH_HOME_APP");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_SUBMIT_SELF_CHECK_BLOCKED, exception.getCode());
    }

    @Test
    void shouldRejectSubmitWhenConcurrencyTokenStillActive() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-330");
        prepay.setPaymentOrderId("PAY-330");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-330")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-330");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-330")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付");
        controlPolicy.setAllowedChannelCodes("wx_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setMinuteSubmitLimit(10);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(controlPolicy);
        when(paymentMapper.countRecentAttemptsBySourceAppAndMethod("housekeeping-app-web", "微信支付")).thenReturn(0);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-330")).thenReturn(false);

        PaymentSubmitConcurrencyTokenDTO token = new PaymentSubmitConcurrencyTokenDTO();
        token.setPrepayOrderNo("PRE-330");
        token.setPaymentOrderId("PAY-330");
        token.setSourceAppId("housekeeping-app-web");
        token.setTokenStatus("ACTIVE");
        token.setHolderIdempotencyKey("IDEMP-OLD-330");
        when(paymentMapper.findSubmitConcurrencyToken("PRE-330", "housekeeping-app-web")).thenReturn(token);
        when(paymentMapper.occupySubmitConcurrencyToken(
                "PRE-330",
                "PAY-330",
                "housekeeping-app-web",
                "IDEMP-330",
                "H5",
                "10.0.0.10",
                120)).thenReturn(0);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-330");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_HOME_APP");
        request.setTerminal("H5");
        request.setClientIp("10.0.0.10");
        request.setIdempotencyKey("IDEMP-330");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_SUBMIT_CONCURRENCY_BLOCKED, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-330");
    }

    @Test
    void shouldReleaseSubmitConcurrencyTokenWhenClosePayment() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CLOSE-1");
        detail.setOrderNo("ORD-CLOSE-1");
        detail.setChannelTransactionNo("CHANNEL-CLOSE-1");
        detail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CLOSE-1")).thenReturn(detail, detail);
        when(paymentMapper.findRouteLogs("PAY-CLOSE-1")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CLOSE-1")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CLOSE-1")).thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-1");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request);

        verify(paymentMapper, times(1)).releaseSubmitConcurrencyToken("PAY-CLOSE-1", "MANUAL_CLOSE");
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

    @Test
    void shouldThrowBusinessExceptionWhenQueryPaymentMissing() {
        when(paymentMapper.findDetail("PAY-MISSING")).thenReturn(null);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, exception.getCode());
    }

    @Test
    void shouldKeepLatestAttemptContextWhenLoadingPaymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-100");
        detail.setLatestTerminal("PC_WEB");
        detail.setLatestClientIp("10.10.1.8");
        detail.setLatestIdempotencyKey("IDEMP-DETAIL-100");
        detail.setLatestAttemptStatus("处理中");
        detail.setLatestAttemptStatusType("info");
        detail.setLatestRequestPayload("{\"paymentOrderId\":\"PAY-DETAIL-100\"}");
        detail.setLatestResponsePayload("{\"code\":\"SUCCESS\"}");
        when(paymentMapper.findDetail("PAY-DETAIL-100")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-DETAIL-100")).thenReturn(Arrays.asList("ROUTE-1"));
        when(paymentMapper.findNotifyLogs("PAY-DETAIL-100")).thenReturn(Arrays.asList("NOTIFY-1"));
        when(paymentMapper.findEventItems("PAY-DETAIL-100")).thenReturn(Arrays.asList("EVENT-1"));

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .detail("PAY-DETAIL-100");

        org.junit.jupiter.api.Assertions.assertEquals("PC_WEB", result.getLatestTerminal());
        org.junit.jupiter.api.Assertions.assertEquals("10.10.1.8", result.getLatestClientIp());
        org.junit.jupiter.api.Assertions.assertEquals("IDEMP-DETAIL-100", result.getLatestIdempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals("处理中", result.getLatestAttemptStatus());
        org.junit.jupiter.api.Assertions.assertEquals("info", result.getLatestAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertEquals("{\"paymentOrderId\":\"PAY-DETAIL-100\"}", result.getLatestRequestPayload());
        org.junit.jupiter.api.Assertions.assertEquals("{\"code\":\"SUCCESS\"}", result.getLatestResponsePayload());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getRouteLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getNotifyLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getEventLogs().size());
    }

    @Test
    void shouldExportPaymentOrdersAsCsv() {
        PaymentListQueryDTO query = new PaymentListQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setPaymentMethod(" 微信支付 ");
        query.setStatus(" SUCCESS ");
        PaymentListItemDTO item = new PaymentListItemDTO();
        item.setPaymentOrderId("PAY-001");
        item.setOrderNo("ORD-001");
        item.setCustomerName("张女士");
        item.setAmount("¥88.00");
        item.setPaymentMethod("微信支付");
        item.setChannel("wx_h5");
        item.setChannelTransactionNo("WX-001");
        item.setStatus("SUCCESS");
        item.setCreatedAt("2026-07-29 10:00:00");
        when(paymentMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .exportCsv(query);

        org.junit.jupiter.api.Assertions.assertEquals("PAY-001", query.getPaymentOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("ORD-001", query.getOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("微信支付", query.getPaymentMethod());
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", query.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("PAY-001"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("张女士"));
        verify(paymentMapper).findAllForExport(query);
    }

    @Test
    void shouldMarkPaymentAsRiskReviewBeforeChannelSubmit() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-RISK-001");
        prepayOrder.setPaymentOrderId("PAY-RISK-001");
        prepayOrder.setOrderNo("ORD-RISK-001");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-RISK-001");
        paymentDetail.setOrderNo("ORD-RISK-001");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");

        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("REVIEW");
        riskDecision.setDecisionType("warn");
        riskDecision.setReviewNo("REVIEW-2001");
        riskDecision.setEventNo("RISK-EVT-2001");
        riskDecision.setRiskTag("疑似高风险手机号");
        riskDecision.setMessage("命中黑名单，需人工复核");

        when(paymentMapper.findPrepay("PRE-RISK-001")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-RISK-001")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-001");
        request.setPaymentMethod("微信支付");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService)
                .submit(request);

        verify(paymentRiskControlService, times(1)).evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class));
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, times(1)).updatePaymentStatus("PAY-RISK-001", "RISK_REVIEW", "warn", "");
        verify(paymentMapper, times(1)).updatePrepayStatusByPaymentOrderId("PAY-RISK-001", "待风控复核", "warn");
        org.junit.jupiter.api.Assertions.assertEquals("PRE-RISK-001", result.getPrepayOrderNo());
    }

    @Test
    void shouldApplyOpsConfigDefaultsWhenSubmitRequestMissesMethodAndChannel() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-OPS-001");
        prepayOrder.setPaymentOrderId("PAY-OPS-001");
        prepayOrder.setOrderNo("ORD-OPS-001");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("268.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-OPS-001");
        paymentDetail.setOrderNo("ORD-OPS-001");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");

        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        riskDecision.setDecisionType("success");
        riskDecision.setMessage("允许进入支付主链路");

        PaymentOpsConfigSnapshotDTO snapshot = new PaymentOpsConfigSnapshotDTO();
        snapshot.setBusinessCode("HOME_CLEAN");
        snapshot.setPayType("PAY_CONSUME");
        snapshot.setTerminalType("APP");
        snapshot.setDefaultPayMethod("微信支付");
        snapshot.setPrimaryChannelProfileCode("CHANNEL_WX_H5");

        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-OPS-001");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");

        when(paymentMapper.findPrepay("PRE-OPS-001")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-OPS-001")).thenReturn(paymentDetail);
        when(paymentOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(snapshot);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("PRE-OPS-001|微信支付|wx_h5")).thenReturn(false);
        when(paymentMapper.updatePrepayToPaying("PRE-OPS-001")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-OPS-001")).thenReturn("ORD-OPS-001");
        when(paymentMapper.findPrepay("PRE-OPS-001")).thenReturn(prepayOrder);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-OPS-001");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService)
                .submit(request);

        org.junit.jupiter.api.Assertions.assertEquals("微信支付", request.getPaymentMethod());
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", request.getChannelCode());
        verify(paymentOpsConfigService, times(1)).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP");
        verify(paymentChannelSubmitService, times(1)).submit(org.mockito.ArgumentMatchers.any());
    }
}
