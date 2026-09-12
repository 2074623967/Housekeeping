package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitConcurrencyTokenDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentChannelQueryAdapter;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelQueryRateLimitService;
import com.abc123.hsp.service.PaymentOpsConfigService;
import com.abc123.hsp.service.PaymentRiskControlService;
import com.abc123.hsp.service.PaymentSubmitRateLimitService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 支付状态收口规则单元测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Test
    void shouldGenerateUniqueBusinessNumbersWithStablePrefix() {
        java.util.Set<String> businessNos = new java.util.HashSet<String>();

        for (int index = 0; index < 1000; index++) {
            String businessNo = PaymentServiceImpl.buildBusinessNo("EVT");
            org.junit.jupiter.api.Assertions.assertTrue(businessNo.matches("EVT\\d{13}[0-9a-f]{8}"));
            org.junit.jupiter.api.Assertions.assertTrue(businessNos.add(businessNo));
        }
    }

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

    @Mock
    private PaymentChannelQueryRateLimitService paymentChannelQueryRateLimitService;

    @Mock
    private PaymentSubmitRateLimitService paymentSubmitRateLimitService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Test
    void shouldRejectInvalidPaymentIdentifiersBeforeAnyMapperInteraction() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        com.abc123.hsp.dto.PaymentQueryRequestDTO queryRequest =
                new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("PAY\nQUERY");
        com.abc123.hsp.dto.PaymentCloseRequestDTO closeRequest =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        closeRequest.setPaymentOrderId(String.join("", Collections.nCopies(65, "P")));
        com.abc123.hsp.dto.PaymentQueryRequestDTO blankOperatorQuery =
                new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        blankOperatorQuery.setPaymentOrderId("PAY-QUERY-OPERATOR");
        com.abc123.hsp.dto.PaymentCloseRequestDTO oversizedOperatorClose =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        oversizedOperatorClose.setPaymentOrderId("PAY-CLOSE-OPERATOR");
        com.abc123.hsp.dto.PaymentQueryRequestDTO controlOperatorQuery =
                new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        controlOperatorQuery.setPaymentOrderId("PAY-QUERY-CONTROL-OPERATOR");

        BusinessException detailException = assertThrows(
                BusinessException.class, () -> service.detail(" "));
        BusinessException cashierException = assertThrows(
                BusinessException.class, () -> service.cashier("PRE\rINJECTED"));
        BusinessException queryException = assertThrows(
                BusinessException.class, () -> service.query(queryRequest));
        BusinessException closeException = assertThrows(
                BusinessException.class, () -> service.close(closeRequest));
        BusinessException blankOperatorException = assertThrows(
                BusinessException.class, () -> service.query(blankOperatorQuery, " "));
        BusinessException oversizedOperatorException = assertThrows(
                BusinessException.class, () -> service.close(
                        oversizedOperatorClose, String.join("", Collections.nCopies(65, "O"))));
        BusinessException controlOperatorException = assertThrows(
                BusinessException.class, () -> service.query(controlOperatorQuery, "finance-ops\n"));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, detailException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, cashierException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, queryException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, closeException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, blankOperatorException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, oversizedOperatorException.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, controlOperatorException.getCode());
        verifyNoInteractions(paymentMapper, paymentChannelQueryRateLimitService);
    }

    @Test
    void shouldRejectInvalidPrepayRequestBeforeAnyMapperInteraction() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD\nINJECTED");
        request.setPayScene("HOME_CLEAN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, exception.getCode());
        verifyNoInteractions(paymentMapper);
    }

    @Test
    void shouldRejectPaymentRequestControlCharactersBeforeTrimming() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        com.abc123.hsp.dto.PaymentQueryRequestDTO queryRequest =
                new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("PAY-QUERY\n");
        com.abc123.hsp.dto.PaymentCloseRequestDTO closeRequest =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        closeRequest.setPaymentOrderId("PAY-CLOSE\n");
        PrepayRequestDTO orderNoRequest = validPrepayRequest();
        orderNoRequest.setOrderNo("ORDER-001\n");
        PrepayRequestDTO paySceneRequest = validPrepayRequest();
        paySceneRequest.setPayScene("HOME_CLEAN\n");
        PrepayRequestDTO customerNameRequest = validPrepayRequest();
        customerNameRequest.setCustomerName("CUSTOMER\n");
        PrepayRequestDTO cashierTitleRequest = validPrepayRequest();
        cashierTitleRequest.setCashierTitle("CASHIER\n");

        assertPaymentRequestInvalid(() -> service.detail("PAY-DETAIL\n"));
        assertPaymentRequestInvalid(() -> service.cashier("PRE-001\n"));
        assertPaymentRequestInvalid(() -> service.query(queryRequest));
        assertPaymentRequestInvalid(() -> service.close(closeRequest));
        assertPaymentRequestInvalid(() -> service.prepay(orderNoRequest));
        assertPaymentRequestInvalid(() -> service.prepay(paySceneRequest));
        assertPaymentRequestInvalid(() -> service.prepay(customerNameRequest));
        assertPaymentRequestInvalid(() -> service.prepay(cashierTitleRequest));

        verifyNoInteractions(paymentMapper, paymentChannelQueryRateLimitService);
    }

    @Test
    void shouldRejectInvalidPrepayAmountBeforeAnyMapperInteraction() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-INVALID-AMOUNT");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("1.001"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, exception.getCode());
        verifyNoInteractions(paymentMapper);
    }

    @Test
    void shouldRejectInvalidPrepayOrderNoBeforeAnySubmitInteraction() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE\nINJECTED");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID,
                exception.getCode());
        verifyNoInteractions(
                paymentMapper,
                paymentChannelRoutingService,
                paymentRiskControlService,
                paymentSubmitRateLimitService,
                paymentChannelSubmitService);
    }

    @Test
    void shouldRejectSubmitControlCharactersBeforeTrimming() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        PaymentSubmitRequestDTO prepayOrderNoRequest = validSubmitRequest();
        prepayOrderNoRequest.setPrepayOrderNo("PRE-001\n");
        PaymentSubmitRequestDTO paymentMethodRequest = validSubmitRequest();
        paymentMethodRequest.setPaymentMethod("微信支付\n");
        PaymentSubmitRequestDTO channelCodeRequest = validSubmitRequest();
        channelCodeRequest.setChannelCode("wx_h5\n");
        PaymentSubmitRequestDTO sourceAppIdRequest = validSubmitRequest();
        sourceAppIdRequest.setSourceAppId("cashier-app\n");
        PaymentSubmitRequestDTO merchantNoRequest = validSubmitRequest();
        merchantNoRequest.setMerchantNo("MCH-001\n");
        PaymentSubmitRequestDTO accessTokenRequest = validSubmitRequest();
        accessTokenRequest.setAccessToken("TOKEN-001\n");
        PaymentSubmitRequestDTO terminalRequest = validSubmitRequest();
        terminalRequest.setTerminal("APP\n");
        PaymentSubmitRequestDTO clientIpRequest = validSubmitRequest();
        clientIpRequest.setClientIp("127.0.0.1\n");
        PaymentSubmitRequestDTO clientDeviceIdRequest = validSubmitRequest();
        clientDeviceIdRequest.setClientDeviceId("DEVICE-001\n");
        PaymentSubmitRequestDTO payerPhoneRequest = validSubmitRequest();
        payerPhoneRequest.setPayerPhone("13800138000\n");
        PaymentSubmitRequestDTO idempotencyKeyRequest = validSubmitRequest();
        idempotencyKeyRequest.setIdempotencyKey("IDEMP-001\n");

        assertSubmitRequestInvalid(service, prepayOrderNoRequest);
        assertSubmitRequestInvalid(service, paymentMethodRequest);
        assertSubmitRequestInvalid(service, channelCodeRequest);
        assertSubmitRequestInvalid(service, sourceAppIdRequest);
        assertSubmitRequestInvalid(service, merchantNoRequest);
        assertSubmitRequestInvalid(service, accessTokenRequest);
        assertSubmitRequestInvalid(service, terminalRequest);
        assertSubmitRequestInvalid(service, clientIpRequest);
        assertSubmitRequestInvalid(service, clientDeviceIdRequest);
        assertSubmitRequestInvalid(service, payerPhoneRequest);
        BusinessException idempotencyException = assertThrows(
                BusinessException.class, () -> service.submit(idempotencyKeyRequest));
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                idempotencyException.getCode());
        verifyNoInteractions(
                paymentMapper,
                paymentChannelRoutingService,
                paymentRiskControlService,
                paymentSubmitRateLimitService,
                paymentChannelSubmitService);
    }

    @Test
    void shouldRejectUnknownTerminalBeforeAnySubmitInteraction() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-INVALID-TERMINAL");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setTerminal("attacker-rotated-terminal");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID,
                exception.getCode());
        verifyNoInteractions(
                paymentMapper,
                paymentChannelRoutingService,
                paymentRiskControlService,
                paymentSubmitRateLimitService,
                paymentChannelSubmitService);
    }

    @Test
    void shouldRejectOversizedRiskFieldBeforeAnySubmitInteraction() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-INVALID-DEVICE");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setClientDeviceId(String.join("", Collections.nCopies(129, "D")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID,
                exception.getCode());
        verifyNoInteractions(
                paymentMapper,
                paymentChannelRoutingService,
                paymentRiskControlService,
                paymentSubmitRateLimitService,
                paymentChannelSubmitService);
    }

    @Test
    void shouldApplySharedRateLimitBeforeCallingPaymentChannel() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-LIMIT", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-LIMIT")).thenReturn(detail);
        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_RATE_LIMITED,
                "rate limited"))
                .when(paymentChannelQueryRateLimitService).acquire("wx_h5", "PAY-QUERY-LIMIT");
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-LIMIT");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                paymentChannelQueryRateLimitService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_QUERY_RATE_LIMITED,
                exception.getCode());
        verify(paymentChannelQueryRateLimitService).acquire("wx_h5", "PAY-QUERY-LIMIT");
        verify(paymentChannelQueryService, never()).query(detail);
    }

    @Test
    void shouldIgnoreLateCallbackWhenPaymentAlreadySucceeded() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-001");
        detail.setChannel("wx_h5");
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
    void shouldSettleLateSuccessCallbackAfterLegacyExpiryClose() {
        PaymentDetailDTO closedDetail = queryDetail("PAY-EXPIRED-CALLBACK", "CLOSED");
        PaymentDetailDTO settledDetail = queryDetail("PAY-EXPIRED-CALLBACK", "SUCCESS");
        when(paymentMapper.findDetail("PAY-EXPIRED-CALLBACK"))
                .thenReturn(closedDetail, closedDetail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-EXPIRED-CALLBACK", "WX-EXPIRED-CALLBACK")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findRouteLogs("PAY-EXPIRED-CALLBACK")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-EXPIRED-CALLBACK")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-EXPIRED-CALLBACK")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-EXPIRED-CALLBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-EXPIRED-CALLBACK");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback);

        verify(paymentMapper).updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info");
        verify(paymentMapper).updateBillAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "已结清", "success");
        verify(paymentMapper).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), eq("PAY-EXPIRED-CALLBACK"),
                eq("ORD-QUERY-001"), eq("{\"channel\":\"wx_h5\"}"));
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
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
    void shouldRejectCallbackFieldsOutsidePersistenceBoundsBeforeVerification() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId(repeat('P', 65));
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("CHANNEL-001");

        assertThrows(IllegalArgumentException.class, () -> service.callback("wx_h5", callback));
        callback.setPaymentOrderId("PAY-001");
        callback.setTradeStatus(repeat('S', 33));
        assertThrows(IllegalArgumentException.class, () -> service.callback("wx_h5", callback));
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo(repeat('T', 129));
        assertThrows(IllegalArgumentException.class, () -> service.callback("wx_h5", callback));
        callback.setChannelTransactionNo("CHANNEL\n001");
        assertThrows(IllegalArgumentException.class, () -> service.callback("wx_h5", callback));
        callback.setChannelTransactionNo("CHANNEL-001");
        assertThrows(IllegalArgumentException.class, () -> service.callback(repeat('C', 65), callback));
        assertThrows(IllegalArgumentException.class, () -> service.callback("wx_h5\n", callback));
        assertThrows(IllegalArgumentException.class, () -> service.callback(" ", callback));

        verify(paymentCallbackSignatureService, never()).verify(
                anyString(), org.mockito.ArgumentMatchers.any(PaymentCallbackRequestDTO.class));
        verify(paymentMapper, never()).findDetail(anyString());
    }

    @Test
    void shouldRejectCallbackFromDifferentPaymentChannel() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-CHANNEL-MISMATCH");
        detail.setChannel("alipay_h5");
        detail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-CHANNEL-MISMATCH")).thenReturn(detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-CHANNEL-MISMATCH");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CROSS-CHANNEL-001");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CALLBACK_CHANNEL_MISMATCH, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertPaymentProcessLog(
                org.mockito.ArgumentMatchers.any(com.abc123.hsp.entity.PaymentProcessLogEntity.class));
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectCallbackForDifferentChannelTransaction() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-TRANSACTION-MISMATCH");
        detail.setChannel("alipay_h5");
        detail.setChannelTransactionNo("ALI-BOUND-001");
        detail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-TRANSACTION-MISMATCH")).thenReturn(detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-TRANSACTION-MISMATCH");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("ALI-OTHER-002");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("alipay_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectCallbackWithoutChannelSubmissionFact() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-NOT-SUBMITTED");
        detail.setOrderNo("ORD-CALLBACK-NOT-SUBMITTED");
        detail.setChannel("wx_h5");
        detail.setStatus("RISK_REJECTED");
        when(paymentMapper.findDetail("PAY-CALLBACK-NOT-SUBMITTED")).thenReturn(detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-NOT-SUBMITTED");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-NOT-SUBMITTED");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).markPaymentPendingFromCallback(anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
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
    void shouldExposePaymentOrderIdOnCashierPage() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-CASHIER-001");
        prepay.setPaymentOrderId("PAY-CASHIER-001");
        when(paymentMapper.findPrepay("PRE-CASHIER-001")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setStatus("WAIT_CALLBACK");
        submissionFact.setLatestAttemptStatus("处理中");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-001")).thenReturn(submissionFact);

        com.abc123.hsp.dto.CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-001");

        org.junit.jupiter.api.Assertions.assertEquals("PAY-CASHIER-001", result.getPaymentOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("WAIT_CALLBACK", result.getPaymentStatus());
        org.junit.jupiter.api.Assertions.assertEquals("处理中", result.getLatestAttemptStatus());
    }

    @Test
    void shouldReuseActivePrepayWhenOrderIsStillOpen() {
        PrepayOrderDTO activePrepay = new PrepayOrderDTO();
        activePrepay.setPrepayOrderNo("PRE-001");
        activePrepay.setPaymentOrderId("PAY-001");
        activePrepay.setPaymentStatus("WAIT_CALLBACK");
        activePrepay.setPayScene("HOME_CLEAN");
        activePrepay.setCustomerName("张女士");
        activePrepay.setAmount("¥168.00");
        activePrepay.setCashierTitle("家政服务收银台");
        when(paymentMapper.lockPrepayCreation("ORD-001")).thenReturn("ORD-001");
        when(paymentMapper.findOrderAmount("ORD-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-001")).thenReturn("张女士");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-001")).thenReturn(activePrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-001");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("168.0"));

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .prepay(request);

        org.mockito.InOrder prepayOrder = org.mockito.Mockito.inOrder(paymentMapper);
        prepayOrder.verify(paymentMapper).initPrepayCreationLock("ORD-001");
        prepayOrder.verify(paymentMapper).lockPrepayCreation("ORD-001");
        prepayOrder.verify(paymentMapper).findOrderAmount("ORD-001");
        prepayOrder.verify(paymentMapper).findPaidAmount("ORD-001");
        prepayOrder.verify(paymentMapper).findCustomerNameByOrderNo("ORD-001");
        prepayOrder.verify(paymentMapper).findLatestActivePrepayByOrderNo("ORD-001");
        verify(paymentMapper, times(1)).findLatestActivePrepayByOrderNo("ORD-001");
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
    void shouldRejectActiveStandardPrepayWhenOrderFactsHaveChanged() {
        PrepayOrderDTO activePrepay = new PrepayOrderDTO();
        activePrepay.setPrepayOrderNo("PRE-STALE");
        activePrepay.setPaymentOrderId("PAY-STALE");
        activePrepay.setPaymentStatus("PREPAY_CREATED");
        activePrepay.setPayScene("HOME_CLEAN");
        activePrepay.setCustomerName("张女士");
        activePrepay.setAmount("¥168.00");
        activePrepay.setCashierTitle("家政服务收银台");
        when(paymentMapper.lockPrepayCreation("ORD-STALE"))
                .thenReturn("ORD-STALE");
        when(paymentMapper.findOrderAmount("ORD-STALE"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-STALE"))
                .thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findCustomerNameByOrderNo("ORD-STALE"))
                .thenReturn("张女士");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-STALE"))
                .thenReturn(activePrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-STALE");
        request.setPayScene("HOME_CLEAN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        verify(paymentMapper, never()).findBillNoByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectVirtualPrepayRetryWhenIdentityFieldsAreMissing() {
        when(paymentMapper.lockPrepayCreation("VIRTUAL-INCOMPLETE"))
                .thenReturn("VIRTUAL-INCOMPLETE");

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("VIRTUAL-INCOMPLETE");
        request.setPayScene("WALLET_RECHARGE_APP");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        verify(paymentMapper, never()).findLatestActivePrepayByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectActivePrepayWhenRequestParametersConflict() {
        PrepayOrderDTO activePrepay = new PrepayOrderDTO();
        activePrepay.setPrepayOrderNo("PRE-CONFLICT");
        activePrepay.setPaymentOrderId("PAY-CONFLICT");
        activePrepay.setPaymentStatus("PREPAY_CREATED");
        activePrepay.setPayScene("WALLET_RECHARGE_APP");
        activePrepay.setCustomerName("张女士");
        activePrepay.setAmount("¥99.00");
        activePrepay.setCashierTitle("钱包充值收银台");
        when(paymentMapper.lockPrepayCreation("WALLET-RECHARGE-CONFLICT"))
                .thenReturn("WALLET-RECHARGE-CONFLICT");
        when(paymentMapper.findLatestActivePrepayByOrderNo("WALLET-RECHARGE-CONFLICT"))
                .thenReturn(activePrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-RECHARGE-CONFLICT");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("100.00"));
        request.setCashierTitle("钱包充值收银台");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        verify(paymentMapper).findLatestActivePrepayByOrderNo("WALLET-RECHARGE-CONFLICT");
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).insertPrepayOrder(
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldCreateNewPrepayWhenNoActivePrepayExists() {
        PrepayOrderDTO createdPrepay = new PrepayOrderDTO();
        createdPrepay.setPrepayOrderNo("PRE-NEW");
        createdPrepay.setPaymentOrderId("PAY-NEW");
        when(paymentMapper.lockPrepayCreation("ORD-002")).thenReturn("ORD-002");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-002")).thenReturn(null);
        when(paymentMapper.findOrderAmount("ORD-002")).thenReturn(new java.math.BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-002")).thenReturn(java.math.BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-002")).thenReturn("张女士");
        when(paymentMapper.findBillNoByOrderNo("ORD-002")).thenReturn("BILL-002");
        when(paymentMapper.findPrepay(org.mockito.ArgumentMatchers.anyString())).thenReturn(createdPrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-002");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("168.0"));
        request.setCashierTitle("订单 ORD-002 收银台");

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
                org.mockito.ArgumentMatchers.eq("订单 ORD-002 收银台"),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-NEW", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-NEW", result.getPaymentOrderId());
    }

    @Test
    void shouldRejectStandardPrepayWhenAmountConflictsWithOrderFacts() {
        when(paymentMapper.lockPrepayCreation("ORD-STANDARD-CONFLICT"))
                .thenReturn("ORD-STANDARD-CONFLICT");
        when(paymentMapper.findOrderAmount("ORD-STANDARD-CONFLICT"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-STANDARD-CONFLICT"))
                .thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findCustomerNameByOrderNo("ORD-STANDARD-CONFLICT"))
                .thenReturn("张女士");

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-STANDARD-CONFLICT");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("149.00"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        verify(paymentMapper, never()).findBillNoByOrderNo(anyString());
        verify(paymentMapper, never()).insertBill(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectStandardPrepayWhenCustomerConflictsWithOrderFacts() {
        when(paymentMapper.lockPrepayCreation("ORD-CUSTOMER-CONFLICT"))
                .thenReturn("ORD-CUSTOMER-CONFLICT");
        when(paymentMapper.findOrderAmount("ORD-CUSTOMER-CONFLICT"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-CUSTOMER-CONFLICT"))
                .thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findCustomerNameByOrderNo("ORD-CUSTOMER-CONFLICT"))
                .thenReturn("张女士");

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-CUSTOMER-CONFLICT");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("李女士");
        request.setAmount(new BigDecimal("148.00"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        verify(paymentMapper, never()).findBillNoByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectSettledOrOverpaidOrderWithoutCreatingPayment() {
        when(paymentMapper.lockPrepayCreation("ORD-SETTLED")).thenReturn("ORD-SETTLED");
        when(paymentMapper.findOrderAmount("ORD-SETTLED")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-SETTLED")).thenReturn(new BigDecimal("168.01"));

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-SETTLED");
        request.setPayScene("HOME_CLEAN");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_ORDER_ALREADY_SETTLED, exception.getCode());
        verify(paymentMapper, never()).insertBill(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                anyString(), anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).insertPrepayOrder(
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldStopPrepayWhenCreationLockCannotBeRead() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-LOCK-MISSING");
        request.setPayScene("HOME_CLEAN");
        when(paymentMapper.lockPrepayCreation("ORD-LOCK-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        verify(paymentMapper).initPrepayCreationLock("ORD-LOCK-MISSING");
        verify(paymentMapper, never()).findLatestActivePrepayByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldCreateVirtualPrepayWhenOrderSourceMissing() {
        PrepayOrderDTO createdPrepay = new PrepayOrderDTO();
        createdPrepay.setPrepayOrderNo("PRE-VIRTUAL");
        createdPrepay.setPaymentOrderId("PAY-VIRTUAL");
        when(paymentMapper.lockPrepayCreation("WALLET-RECHARGE-001"))
                .thenReturn("WALLET-RECHARGE-001");
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
        detail.setChannel("alipay_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥99.00");
        when(paymentMapper.findDetail("PAY-VIRTUAL-CALLBACK")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("WALLET-RECHARGE-001"))
                .thenReturn("WALLET-RECHARGE-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-VIRTUAL-CALLBACK", "ALI-VIRTUAL-001")).thenReturn(1);
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
        detail.setChannel("wx_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-001")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-CALLBACK-001"))
                .thenReturn("ORD-CALLBACK-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-001", "WX-CALLBACK-001")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-CALLBACK-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-001")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-001");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-001");

        TransactionSynchronizationManager.initSynchronization();
        try {
            new PaymentServiceImpl(
                    paymentMapper,
                    paymentCallbackSignatureService,
                    paymentChannelRoutingService,
                    paymentChannelQueryService,
                    paymentChannelSubmitService,
                    paymentEventDispatchService)
                    .callback("wx_h5", callback);

            verify(paymentEventDispatchService, never()).publishPaymentSuccess(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-001")
            );
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_SUCCESS"),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-001"),
                org.mockito.ArgumentMatchers.eq("ORD-CALLBACK-001"),
                org.mockito.ArgumentMatchers.eq("{\"channel\":\"wx_h5\"}")
        );
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_NOTIFY".equals(log.getSourceType())
                        && "PAY-CALLBACK-001".equals(log.getPaymentOrderId())
                        && "渠道回调".equals(log.getProcessStage())));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_EVENT".equals(log.getSourceType())
                        && "ORD-CALLBACK-001".equals(log.getOrderNo())
                        && log.getMessage().contains("PAYMENT_SUCCESS")));
        verify(paymentEventDispatchService).publishPaymentSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-001")
        );
        verify(paymentMapper).releaseSubmitConcurrencyToken("PAY-CALLBACK-001", "CALLBACK_SUCCESS");
        org.mockito.InOrder settlementOrder = org.mockito.Mockito.inOrder(paymentMapper);
        settlementOrder.verify(paymentMapper).initPrepayCreationLock("ORD-CALLBACK-001");
        settlementOrder.verify(paymentMapper).lockPrepayCreation("ORD-CALLBACK-001");
        settlementOrder.verify(paymentMapper).markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-001", "WX-CALLBACK-001");
        settlementOrder.verify(paymentMapper).updateOrderAfterPayment(
                "ORD-CALLBACK-001", new BigDecimal("168.00"), "待履约", "info");
    }

    @Test
    void shouldNotPublishDownstreamEventWhenCallbackStillPending() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CALLBACK-002");
        detail.setOrderNo("ORD-CALLBACK-002");
        detail.setChannel("wx_h5");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-002")).thenReturn(detail, detail);
        when(paymentMapper.markPaymentPendingFromCallback(
                "PAY-CALLBACK-002", "WX-CALLBACK-002")).thenReturn(1);
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-002");
        callback.setTradeStatus("PROCESS\"ING");
        callback.setChannelTransactionNo("WX-CALLBACK-002");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback(" WX_H5 ", callback);

        verify(paymentCallbackSignatureService).verify("wx_h5", callback);
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-CALLBACK-002"), eq("wx_h5"), eq("PROCESS\"ING"),
                eq("{\"tradeStatus\":\"PROCESS\\\"ING\"}"), eq("{\"code\":\"SUCCESS\"}"),
                eq("已收口"), eq("success"));

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
    void shouldNotLetPendingCallbackOverwriteConcurrentSuccess() {
        PaymentDetailDTO pendingDetail = queryDetail("PAY-CALLBACK-PENDING-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-CALLBACK-PENDING-RACE", "SUCCESS");
        when(paymentMapper.findDetail("PAY-CALLBACK-PENDING-RACE"))
                .thenReturn(pendingDetail, settledDetail);
        when(paymentMapper.markPaymentPendingFromCallback(
                "PAY-CALLBACK-PENDING-RACE", "WX-CALLBACK-PENDING-RACE")).thenReturn(0);
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-PENDING-RACE")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-PENDING-RACE")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-PENDING-RACE")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-PENDING-RACE");
        callback.setTradeStatus("PROCESSING");
        callback.setChannelTransactionNo("WX-CALLBACK-PENDING-RACE");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback);

        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldNotSettleWhenAnotherSuccessCallbackAlreadyWon() {
        PaymentDetailDTO pendingDetail = queryDetail("PAY-CALLBACK-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-CALLBACK-RACE", "SUCCESS");
        when(paymentMapper.findDetail("PAY-CALLBACK-RACE"))
                .thenReturn(pendingDetail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-RACE")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-RACE")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-RACE")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-RACE");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-RACE");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback);

        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldStopSuccessCallbackWhenSettlementLockCannotBeRead() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-LOCK-MISSING", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-LOCK-MISSING")).thenReturn(detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn(null);
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-LOCK-MISSING");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-LOCK-MISSING");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService).callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        verify(paymentMapper).initPrepayCreationLock("ORD-QUERY-001");
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
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
        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setAllowedPaymentMethods("微信支付");
        controlPolicy.setAllowedChannelCodes("wx_h5");
        controlPolicy.setAllowedMerchantNos("MCH_DEFAULT");
        controlPolicy.setMinuteSubmitLimit(3);
        controlPolicy.setInterfaceMinuteSubmitLimit(2);
        controlPolicy.setTokenAuthRequired("关闭");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("default-app")).thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-200");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setMerchantNo("MCH_DEFAULT");
        request.setIdempotencyKey("IDEMP-200");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        service.setPaymentSubmitRateLimitService(paymentSubmitRateLimitService);
        PrepayOrderDTO result = service.submit(request);

        verifyNoInteractions(paymentSubmitRateLimitService);
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
    void shouldRejectMissingSubmitAdapterBeforeWritingChannelSubmissionFacts() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-NO-ADAPTER");
        prepay.setPaymentOrderId("PAY-NO-ADAPTER");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("测试客户");
        when(paymentMapper.findPrepay("PRE-NO-ADAPTER")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-NO-ADAPTER");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-NO-ADAPTER")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING,
                "no payment channel submit adapter available"))
                .when(paymentChannelSubmitService).ensureAdapterAvailable("wx_h5");

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-NO-ADAPTER");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING, exception.getCode());
        verify(paymentMapper, never()).findOrderNoByPrepayOrderNo(anyString());
        verify(paymentMapper, never()).updatePrepayToPaying(anyString());
        verify(paymentMapper, never()).insertRouteRecord(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentChannelSubmitService, never()).submit(
                org.mockito.ArgumentMatchers.any(PaymentChannelSubmitRequestDTO.class));
    }

    @Test
    void shouldRejectOversizedSubmitIdempotencyKeyBeforeControlAndChannelCalls() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-IDEMP-LONG");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey(repeat("A", 129));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                exception.getCode());
        verify(paymentMapper, never()).findPrepay(org.mockito.ArgumentMatchers.anyString());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).existsPaymentAttemptByIdempotencyKey(
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).insertSubmitConcurrencyToken(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectControlCharactersInSubmitIdempotencyKey() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-IDEMP-CTRL");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-CTRL\nINJECTED");

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID,
                exception.getCode());
        verify(paymentMapper, never()).findPrepay(org.mockito.ArgumentMatchers.anyString());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
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
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-\"300")).thenReturn(false);
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
        when(paymentMapper.findSubmitConcurrencyToken("PRE-300", "housekeeping-pc-web")).thenReturn(null);
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("CHANNEL-300");
        submitResult.setAttemptStatus("处理中");
        submitResult.setAttemptStatusType("info");
        submitResult.setChannelReturnCode("BANK_TRANSFER_PENDING");
        submitResult.setStandardizedCode("PAYMENT-CHANNEL-UNMAPPED");
        submitResult.setStandardizedMessage("未配置渠道返回码映射");
        submitResult.setHandlingSuggestion("保留渠道原始事实并转人工判断");
        submitResult.setRetryable("未知");
        submitResult.setManualInterventionRequired("是");
        submitResult.setReturnCodeMappingMatched(Boolean.FALSE);
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
        request.setIdempotencyKey("IDEMP-\"300");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .submit(request);

        verify(paymentMapper, times(1)).updatePaymentMethodAndChannel("PAY-300", "银行转账", "offline_bank", "CHANNEL-300");
        verify(paymentMapper).updatePaymentReturnCodeSnapshot(
                "PAY-300", "BANK_TRANSFER_PENDING", "PAYMENT-CHANNEL-UNMAPPED",
                "未配置渠道返回码映射", "保留渠道原始事实并转人工判断",
                "未知", "是", null, Boolean.FALSE);
        verify(paymentMapper, times(1)).insertSubmitConcurrencyToken(
                org.mockito.ArgumentMatchers.eq("PRE-300"),
                org.mockito.ArgumentMatchers.eq("PAY-300"),
                org.mockito.ArgumentMatchers.eq("housekeeping-pc-web"),
                org.mockito.ArgumentMatchers.eq("IDEMP-\"300"),
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
                org.mockito.ArgumentMatchers.eq("IDEMP-\"300"),
                org.mockito.ArgumentMatchers.argThat(payload ->
                        payload != null
                                && payload.contains("\"sourceAppId\":\"housekeeping-pc-web\"")
                                && payload.contains("\"resolvedChannelCode\":\"offline_bank\"")
                                && payload.contains("\"idempotencyKey\":\"IDEMP-\\\"300\"")),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("渠道提交中"),
                org.mockito.ArgumentMatchers.eq("warn"));
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("{\"code\":\"SUCCESS\",\"channelTransactionNo\":\"CHANNEL-300\"}"),
                org.mockito.ArgumentMatchers.eq("处理中"),
                org.mockito.ArgumentMatchers.eq("info"));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_ROUTE".equals(log.getSourceType())
                        && "PAY-300".equals(log.getPaymentOrderId())
                        && "ORD-300".equals(log.getOrderNo())));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_ATTEMPT".equals(log.getSourceType())
                        && log.getRequestNo().equals(log.getSourceNo())
                        && log.getMessage().contains("请求状态=渠道提交中")
                        && !log.getMessage().contains("10.0.0.3")
                        && !log.getMessage().contains("IDEMP-\"300")
                        && !log.getMessage().contains("请求报文")));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_EVENT".equals(log.getSourceType())
                        && log.getMessage().contains("PAYMENT_SUBMIT")
                        && !log.getMessage().contains("eventPayload")));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "PAYMENT_NOTIFY".equals(log.getSourceType())
                        && "WARN".equals(log.getLogLevel())
                        && "offline_bank".equals(log.getSource())
                        && !log.getMessage().contains("CHANNEL-300")
                        && !log.getMessage().contains("；结果=")));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-300", result.getPrepayOrderNo());
    }

    @Test
    void shouldPersistUnknownResultWhenChannelAcceptedButFinalizationFails() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-UNKNOWN-001");
        prepay.setPaymentOrderId("PAY-UNKNOWN-001");
        prepay.setOrderNo("ORD-UNKNOWN-001");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-UNKNOWN-001")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-UNKNOWN-001");
        detail.setOrderNo("ORD-UNKNOWN-001");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-UNKNOWN-001")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class))).thenReturn(riskDecision);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-UNKNOWN-001")).thenReturn("ORD-UNKNOWN-001");
        when(paymentMapper.updatePrepayToPaying("PRE-UNKNOWN-001")).thenReturn(1);
        when(transactionManager.getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class))).thenReturn(transactionStatus);

        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-UNKNOWN-001");
        submitResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);
        org.mockito.Mockito.doThrow(new IllegalStateException(
                        "jdbc:mysql://internal/payment password=submit-secret\ninvalid SQL"))
                .when(paymentMapper).updatePaymentAttemptSubmitResult(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("{\"code\":\"ACCEPTED\"}"),
                        org.mockito.ArgumentMatchers.eq("待回调"),
                        org.mockito.ArgumentMatchers.eq("warn"));

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-UNKNOWN-001");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-UNKNOWN-001");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                paymentChannelQueryRateLimitService,
                new com.abc123.hsp.service.support.PaymentSensitiveDataMasker(
                        new com.fasterxml.jackson.databind.ObjectMapper()),
                transactionManager);

        Logger transactionLogger = (Logger) org.slf4j.LoggerFactory.getLogger(TransactionTemplate.class);
        ListAppender<ILoggingEvent> transactionAppender = new ListAppender<>();
        transactionAppender.start();
        transactionLogger.addAppender(transactionAppender);
        RuntimeException exception;
        try {
            exception = assertThrows(RuntimeException.class, () -> service.submit(request));
        } finally {
            transactionLogger.detachAppender(transactionAppender);
            transactionAppender.stop();
        }

        org.junit.jupiter.api.Assertions.assertEquals(
                "PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(0, exception.getStackTrace().length);
        org.junit.jupiter.api.Assertions.assertEquals(0, exception.getSuppressed().length);
        org.junit.jupiter.api.Assertions.assertTrue(transactionAppender.list.stream().anyMatch(event ->
                event.getThrowableProxy() != null
                        && "PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException"
                        .equals(event.getThrowableProxy().getMessage())
                        && event.getThrowableProxy().getCause() == null
                        && event.getThrowableProxy().getStackTraceElementProxyArray().length == 0));
        org.junit.jupiter.api.Assertions.assertTrue(transactionAppender.list.stream().noneMatch(event ->
                event.getFormattedMessage().contains("submit-secret")
                        || (event.getThrowableProxy() != null
                        && String.valueOf(event.getThrowableProxy().getMessage()).contains("submit-secret"))));

        org.mockito.InOrder transactionOrder = org.mockito.Mockito.inOrder(
                transactionManager, paymentChannelSubmitService);
        transactionOrder.verify(transactionManager).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
        transactionOrder.verify(transactionManager).commit(transactionStatus);
        transactionOrder.verify(paymentChannelSubmitService).submit(org.mockito.ArgumentMatchers.any());
        transactionOrder.verify(transactionManager).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
        transactionOrder.verify(transactionManager).rollback(transactionStatus);
        transactionOrder.verify(transactionManager).getTransaction(
                org.mockito.ArgumentMatchers.any(TransactionDefinition.class));
        transactionOrder.verify(transactionManager).commit(transactionStatus);
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("{\"code\":\"ACCEPTED\"}"),
                org.mockito.ArgumentMatchers.eq("结果未知，待查单"),
                org.mockito.ArgumentMatchers.eq("danger"));
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-UNKNOWN-001", "结果待确认", "danger");
        verify(paymentMapper).updatePaymentStatus(
                "PAY-UNKNOWN-001", "WAIT_CALLBACK", "danger", "WX-UNKNOWN-001");
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                "PAY-UNKNOWN-001", "SUBMIT_EXCEPTION");
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                "PAY-UNKNOWN-001", "待支付", "warn");
    }

    @Test
    void shouldKeepInvalidChannelSubmitResultUnknownWithoutBindingExternalFacts() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-INVALID-RESULT");
        prepay.setPaymentOrderId("PAY-INVALID-RESULT");
        prepay.setOrderNo("ORD-INVALID-RESULT");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-INVALID-RESULT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-INVALID-RESULT");
        detail.setOrderNo("ORD-INVALID-RESULT");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-INVALID-RESULT")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-INVALID-RESULT"))
                .thenReturn("ORD-INVALID-RESULT");
        when(paymentMapper.updatePrepayToPaying("PRE-INVALID-RESULT")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(
                        ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                        "payment channel submit result is invalid"));

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-INVALID-RESULT");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-INVALID-RESULT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                anyString(), org.mockito.ArgumentMatchers.isNull(),
                eq("结果未知，待查单"), eq("danger"));
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-INVALID-RESULT", "结果待确认", "danger");
        verify(paymentMapper, never()).updatePaymentStatus(
                eq("PAY-INVALID-RESULT"), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUBMIT"), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), eq("PAY-INVALID-RESULT"), anyString(), eq("SUBMIT"),
                anyString(), anyString(), anyString(), anyString());
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
        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED,
                "当前接口提交过于频繁，请稍后重试"))
                .when(paymentSubmitRateLimitService)
                .acquire("housekeeping-app-web", "微信支付", "H5", "10.0.0.20", 2, 10);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-318");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setSourceAppId("housekeeping-app-web");
        request.setMerchantNo("MCH_HOME_APP");
        request.setTerminal("H5_WEB");
        request.setClientIp("10.0.0.20");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        service.setPaymentSubmitRateLimitService(paymentSubmitRateLimitService);
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.submit(request)
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED, exception.getCode());
        verify(paymentSubmitRateLimitService)
                .acquire("housekeeping-app-web", "微信支付", "H5", "10.0.0.20", 2, 10);
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
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-CLOSE-1")).thenReturn(detail, detail);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-1")).thenReturn(1);
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
                .close(request, " 财务运营 ");

        verify(paymentMapper, times(1)).releaseSubmitConcurrencyToken("PAY-CLOSE-1", "MANUAL_CLOSE");
        verify(paymentMapper).insertEvent(
                anyString(),
                eq("PAYMENT_CLOSED"),
                eq("PAY-CLOSE-1"),
                eq("ORD-CLOSE-1"),
                eq("{\"close\":\"manual\",\"initiatedBy\":\"财务运营\"}"));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "业务事件".equals(log.getProcessStage())
                        && "财务运营".equals(log.getSource())));
    }

    @Test
    void shouldRejectLocalCloseAfterPaymentWasSubmittedToChannel() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CLOSE-SUBMITTED");
        detail.setOrderNo("ORD-CLOSE-SUBMITTED");
        detail.setChannelTransactionNo("WX-CLOSE-SUBMITTED");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        when(paymentMapper.findDetail("PAY-CLOSE-SUBMITTED")).thenReturn(detail, detail);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-SUBMITTED");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CLOSE_CHANNEL_CONFIRMATION_REQUIRED, exception.getCode());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                "PAY-CLOSE-SUBMITTED", "MANUAL_CLOSE");
    }

    @Test
    void shouldKeepSubmitTokenWhenLegacySubmittedPaymentIsAlreadyClosed() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CLOSE-LEGACY");
        detail.setOrderNo("ORD-CLOSE-LEGACY");
        detail.setStatus("CLOSED");
        detail.setLatestAttemptStatus("结果未知，待查单");
        when(paymentMapper.findDetail("PAY-CLOSE-LEGACY")).thenReturn(detail);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-LEGACY");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request);

        org.junit.jupiter.api.Assertions.assertEquals("CLOSED", result.getStatus());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                "PAY-CLOSE-LEGACY", "CLOSE_ALREADY_FINALIZED");
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
        PaymentChannelQueryService queryService = new PaymentChannelQueryServiceImpl(
                Arrays.asList(adapter),
                new PaymentChannelReturnCodeStandardizer(
                        org.mockito.Mockito.mock(com.abc123.hsp.mapper.PaymentConfigMapper.class),
                        new com.fasterxml.jackson.databind.ObjectMapper()));

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-1001");
        detail.setChannel("wx_h5");

        PaymentChannelQueryResultDTO result = queryService.query(detail);

        org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-1001", result.getChannelTransactionNo());
        org.junit.jupiter.api.Assertions.assertEquals("LOCAL_SIMULATION", result.getQuerySource());
    }

    @Test
    void shouldSettlePaymentWhenActiveQueryConfirmsSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-001", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-QUERY-001", "SUCCESS");
        when(paymentMapper.findDetail("PAY-QUERY-001")).thenReturn(detail, detail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult("PAY-QUERY-001", "WX-QUERY-001"))
                .thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findRouteLogs("PAY-QUERY-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-001")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-001")).thenReturn(Collections.emptyList());
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-001", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-001");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request, " 支付运营 ");

        verify(paymentMapper).updatePaymentAttemptStatusByPaymentOrderId(
                "PAY-QUERY-001", "成功", "success");
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-QUERY-001", "支付成功", "success");
        verify(paymentMapper).updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info");
        verify(paymentMapper).updateBillAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "已结清", "success");
        verify(paymentMapper).insertEvent(
                anyString(),
                eq("PAYMENT_SUCCESS"),
                eq("PAY-QUERY-001"),
                eq("ORD-QUERY-001"),
                eq("{\"channel\":\"wx_h5\",\"source\":\"ACTIVE_QUERY\","
                        + "\"initiatedBy\":\"支付运营\"}"));
        verify(paymentEventDispatchService).publishPaymentSuccess(anyString(), eq("PAY-QUERY-001"));
        verify(paymentMapper).releaseSubmitConcurrencyToken(
                "PAY-QUERY-001", "ACTIVE_QUERY_SUCCESS");
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("GATEWAY_ACCESS_HTTP", result.getQuerySource());
        org.mockito.InOrder settlementOrder = org.mockito.Mockito.inOrder(paymentMapper);
        settlementOrder.verify(paymentMapper).initPrepayCreationLock("ORD-QUERY-001");
        settlementOrder.verify(paymentMapper).lockPrepayCreation("ORD-QUERY-001");
        settlementOrder.verify(paymentMapper).updatePaymentReturnCodeSnapshot(
                eq("PAY-QUERY-001"), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());
        settlementOrder.verify(paymentMapper).markPaymentSuccessFromChannelResult(
                "PAY-QUERY-001", "WX-QUERY-001");
    }

    @Test
    void shouldRejectActiveQueryWithoutChannelSubmissionFact() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-NOT-SUBMITTED", "WAIT_CALLBACK");
        detail.setLatestAttemptStatus(null);
        when(paymentMapper.findDetail("PAY-QUERY-NOT-SUBMITTED")).thenReturn(detail);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-NOT-SUBMITTED");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService,
                paymentChannelQueryRateLimitService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelQueryRateLimitService, paymentChannelQueryService);
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldOnlyRecordQueryWhenChannelStatusIsNotSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-002", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-002")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-QUERY-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-002")).thenReturn(Collections.emptyList());
        PaymentChannelQueryResultDTO standardizedResult =
                queryResult("WAIT_CALLBACK", "WX-QUERY-002", "GATEWAY_ACCESS_HTTP");
        standardizedResult.setChannelReturnCode("USERPAYING");
        standardizedResult.setStandardizedCode("PAYMENT-CHANNEL-1001");
        standardizedResult.setStandardizedMessage("用户支付中");
        standardizedResult.setHandlingSuggestion("等待回调或主动查单");
        standardizedResult.setRetryable("是");
        standardizedResult.setManualInterventionRequired("否");
        standardizedResult.setMappingVersion("v2026.07.1");
        standardizedResult.setReturnCodeMappingMatched(Boolean.TRUE);
        when(paymentChannelQueryService.query(detail)).thenReturn(standardizedResult);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-002");
        request.setOperator("forged-operator");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request);

        org.junit.jupiter.api.Assertions.assertEquals("USERPAYING", result.getChannelReturnCode());
        org.junit.jupiter.api.Assertions.assertEquals("PAYMENT-CHANNEL-1001", result.getStandardizedCode());
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, result.getReturnCodeMappingMatched());
        verify(paymentMapper).updatePaymentReturnCodeSnapshot(
                "PAY-QUERY-002", "USERPAYING", "PAYMENT-CHANNEL-1001", "用户支付中",
                "等待回调或主动查单", "是", "否", "v2026.07.1", Boolean.TRUE);

        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-002"), eq("wx_h5"), eq("WAIT_CALLBACK"),
                eq("{\"querySource\":\"GATEWAY_ACCESS_HTTP\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                eq("{\"code\":\"SUCCESS\",\"channelReturnCode\":\"USERPAYING\","
                        + "\"standardizedCode\":\"PAYMENT-CHANNEL-1001\","
                        + "\"standardizedMessage\":\"用户支付中\","
                        + "\"handlingSuggestion\":\"等待回调或主动查单\","
                        + "\"retryable\":\"是\",\"manualInterventionRequired\":\"否\","
                        + "\"mappingVersion\":\"v2026.07.1\",\"returnCodeMappingMatched\":true}"),
                eq("已查单"), eq("info"));
    }

    @Test
    void shouldRecordFailedChannelQueryForAutomaticBackoff() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-FAIL", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-FAIL")).thenReturn(detail);
        when(paymentChannelQueryService.query(detail)).thenThrow(new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                "gateway unavailable: jdbc:mysql://internal-db/payment?password=query-secret"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-FAIL");

        assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request, "payment-query-scheduler"));

        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-FAIL"), eq("wx_h5"), eq("QUERY_FAILED"),
                eq("{\"querySource\":\"CHANNEL_QUERY\","
                        + "\"initiatedBy\":\"payment-query-scheduler\"}"),
                eq("{\"code\":\"PAYMENT-1026\",\"message\":\"channel query failed\"}"),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper).insertPaymentProcessLog(argThat(log ->
                "主动查单".equals(log.getProcessStage())
                        && "payment-query-scheduler".equals(log.getSource())
                        && "ERROR".equals(log.getLogLevel())));
    }

    @Test
    void shouldNormalizeUnexpectedChannelQueryFailureAndRecordAutomaticBackoff() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-RUNTIME-FAIL", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-RUNTIME-FAIL")).thenReturn(detail);
        String sensitiveDetail = "jdbc:mysql://internal-db/payment?password=query-secret\n"
                + "select * from payment_order where token='query-token'";
        when(paymentChannelQueryService.query(detail)).thenThrow(new IllegalStateException(sensitiveDetail));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-RUNTIME-FAIL");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "payment channel query failed unexpectedly", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(sensitiveDetail));
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        org.junit.jupiter.api.Assertions.assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        org.junit.jupiter.api.Assertions.assertEquals(0, exception.getSuppressed().length);
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-RUNTIME-FAIL"), eq("wx_h5"), eq("QUERY_FAILED"),
                eq("{\"querySource\":\"CHANNEL_QUERY\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                eq("{\"code\":\"PAYMENT-1026\",\"message\":\"channel query failed\"}"),
                eq("查单失败"), eq("danger"));
    }

    @Test
    void shouldRejectSuccessfulChannelQueryWithoutTransactionBeforeRecordingSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-MISSING-TXN", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-MISSING-TXN")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", null, "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-MISSING-TXN");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-MISSING-TXN"), eq("wx_h5"), eq("QUERY_FAILED"),
                eq("{\"querySource\":\"CHANNEL_QUERY\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                eq("{\"code\":\"PAYMENT-1026\",\"message\":\"channel query failed\"}"),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldUseCallbackTransactionWhenCallbackWinsMissingTransactionQuery() {
        PaymentDetailDTO querying = queryDetail("PAY-QUERY-CALLBACK-TXN", "WAIT_CALLBACK");
        PaymentDetailDTO settled = queryDetail("PAY-QUERY-CALLBACK-TXN", "SUCCESS");
        settled.setChannelTransactionNo("WX-CALLBACK-001");
        when(paymentMapper.findDetail("PAY-QUERY-CALLBACK-TXN")).thenReturn(querying, settled);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(querying))
                .thenReturn(queryResult("SUCCESS", null, "GATEWAY_ACCESS_HTTP"));
        when(paymentMapper.findRouteLogs("PAY-QUERY-CALLBACK-TXN")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-CALLBACK-TXN")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-CALLBACK-TXN")).thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-CALLBACK-TXN");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request);

        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("WX-CALLBACK-001", result.getChannelTransactionNo());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-CALLBACK-TXN"), eq("wx_h5"), eq("SUCCESS"),
                eq("{\"querySource\":\"GATEWAY_ACCESS_HTTP\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                org.mockito.ArgumentMatchers.contains("\"code\":\"SUCCESS\""),
                eq("已查单"), eq("success"));
    }

    @Test
    void shouldRecordInvalidChannelQueryResultWithoutSettlementSideEffects() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-INVALID", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-INVALID")).thenReturn(detail);
        PaymentChannelQueryResultDTO invalidResult = queryResult(
                "SUCCESS", "WX-QUERY-INVALID", "GATEWAY_ACCESS_HTTP\n");
        when(paymentChannelQueryService.query(detail)).thenReturn(invalidResult);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-INVALID");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-INVALID"), eq("wx_h5"), eq("QUERY_FAILED"),
                eq("{\"querySource\":\"CHANNEL_QUERY\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                eq("{\"code\":\"PAYMENT-1026\",\"message\":\"channel query failed\"}"),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldNotReopenClosedPaymentWhenQueryReportsSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-003", "CLOSED");
        when(paymentMapper.findDetail("PAY-QUERY-003")).thenReturn(detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.findRouteLogs("PAY-QUERY-003")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-003")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-003")).thenReturn(Collections.emptyList());
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-003", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-003");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request);

        verify(paymentMapper).markPaymentSuccessFromChannelResult(
                "PAY-QUERY-003", "WX-QUERY-003");
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldNotRepeatSettlementWhenAnotherQueryAlreadyWon() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-004", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-QUERY-004", "SUCCESS");
        when(paymentMapper.findDetail("PAY-QUERY-004")).thenReturn(detail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.findRouteLogs("PAY-QUERY-004")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-004")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-004")).thenReturn(Collections.emptyList());
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-004", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-004");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request);

        verify(paymentMapper, never()).updatePaymentAttemptStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    private PaymentDetailDTO queryDetail(String paymentOrderId, String status) {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId(paymentOrderId);
        detail.setOrderNo("ORD-QUERY-001");
        detail.setChannel("wx_h5");
        detail.setAmount("¥168.00");
        detail.setStatus(status);
        if ("WAIT_CALLBACK".equals(status)) {
            detail.setLatestAttemptStatus("待回调");
        }
        return detail;
    }

    private String repeat(char value, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, value);
        return new String(characters);
    }

    private PaymentChannelQueryResultDTO queryResult(
            String tradeStatus,
            String channelTransactionNo,
            String querySource) {
        PaymentChannelQueryResultDTO result = new PaymentChannelQueryResultDTO();
        result.setTradeStatus(tradeStatus);
        result.setChannelTransactionNo(channelTransactionNo);
        result.setQuerySource(querySource);
        return result;
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
        detail.setCustomerName("张女士");
        detail.setLatestTerminal("PC_WEB");
        detail.setLatestClientIp("10.10.1.8");
        detail.setLatestIdempotencyKey("IDEMP-DETAIL-100");
        detail.setLatestAttemptStatus("处理中");
        detail.setLatestAttemptStatusType("info");
        detail.setLatestRequestPayload("{\"paymentOrderId\":\"PAY-DETAIL-100\",\"payerPhone\":\"13800138000\"}");
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
        org.junit.jupiter.api.Assertions.assertEquals("张*", result.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals("10.10.*.*", result.getLatestClientIp());
        org.junit.jupiter.api.Assertions.assertEquals("IDEMP-****-100", result.getLatestIdempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals("处理中", result.getLatestAttemptStatus());
        org.junit.jupiter.api.Assertions.assertEquals("info", result.getLatestAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertTrue(result.getLatestRequestPayload().contains("138****8000"));
        org.junit.jupiter.api.Assertions.assertEquals("{\"code\":\"SUCCESS\"}", result.getLatestResponsePayload());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getRouteLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getNotifyLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getEventLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals("张女士", detail.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals("10.10.1.8", detail.getLatestClientIp());
        org.junit.jupiter.api.Assertions.assertEquals("IDEMP-DETAIL-100", detail.getLatestIdempotencyKey());
        org.junit.jupiter.api.Assertions.assertTrue(detail.getLatestRequestPayload().contains("13800138000"));
    }

    @Test
    void shouldMaskPaymentListWithoutChangingMapperDto() {
        PaymentListQueryDTO query = new PaymentListQueryDTO();
        query.setPageNo(1);
        query.setPageSize(20);
        PaymentListItemDTO item = new PaymentListItemDTO();
        item.setPaymentOrderId("PAY-001");
        item.setCustomerName("张女士");
        item.setLatestAttemptStatus("处理中");
        when(paymentMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        when(paymentMapper.count(query)).thenReturn(1L);

        PageResultDTO<PaymentListItemDTO> result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .list(query);

        org.junit.jupiter.api.Assertions.assertEquals("张*", result.getItems().get(0).getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals("处理中", result.getItems().get(0).getLatestAttemptStatus());
        org.junit.jupiter.api.Assertions.assertEquals("张女士", item.getCustomerName());
    }

    @Test
    void shouldExportPaymentOrdersAsCsv() {
        PaymentListQueryDTO query = new PaymentListQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setChannelCode(" wx_h5 ");
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
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", query.getChannelCode());
        org.junit.jupiter.api.Assertions.assertEquals("微信支付", query.getPaymentMethod());
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", query.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("PAY-001"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"张*\""));
        org.junit.jupiter.api.Assertions.assertFalse(csv.contains("张女士"));
        org.junit.jupiter.api.Assertions.assertEquals("张女士", item.getCustomerName());
        verify(paymentMapper).findAllForExport(query);
    }

    @Test
    void shouldRejectInvalidPaymentListQueryFieldsBeforeMapperAccess() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        PaymentListQueryDTO oversizedPaymentOrderId = paymentListQuery(
                repeat("P", 65), null, null, null, null);
        PaymentListQueryDTO controlPaymentOrderId = paymentListQuery(
                "PAY-001\n", null, null, null, null);
        PaymentListQueryDTO oversizedOrderNo = paymentListQuery(
                null, repeat("O", 65), null, null, null);
        PaymentListQueryDTO controlOrderNo = paymentListQuery(
                null, "ORD-001\n", null, null, null);
        PaymentListQueryDTO oversizedChannelCode = paymentListQuery(
                null, null, repeat("C", 65), null, null);
        PaymentListQueryDTO controlChannelCode = paymentListQuery(
                null, null, "wx_h5\n", null, null);
        PaymentListQueryDTO oversizedPaymentMethod = paymentListQuery(
                null, null, null, repeat("M", 33), null);
        PaymentListQueryDTO controlPaymentMethod = paymentListQuery(
                null, null, null, "微信支付\n", null);
        PaymentListQueryDTO oversizedStatus = paymentListQuery(
                null, null, null, null, repeat("S", 33));
        PaymentListQueryDTO controlStatus = paymentListQuery(
                null, null, null, null, "SUCCESS\n");

        assertPaymentRequestInvalid(() -> service.list(oversizedPaymentOrderId));
        assertPaymentRequestInvalid(() -> service.list(controlPaymentOrderId));
        assertPaymentRequestInvalid(() -> service.list(oversizedOrderNo));
        assertPaymentRequestInvalid(() -> service.list(controlOrderNo));
        assertPaymentRequestInvalid(() -> service.list(oversizedChannelCode));
        assertPaymentRequestInvalid(() -> service.list(controlChannelCode));
        assertPaymentRequestInvalid(() -> service.list(oversizedPaymentMethod));
        assertPaymentRequestInvalid(() -> service.list(controlPaymentMethod));
        assertPaymentRequestInvalid(() -> service.list(oversizedStatus));
        assertPaymentRequestInvalid(() -> service.list(controlStatus));
        assertPaymentRequestInvalid(() -> service.exportCsv(controlPaymentOrderId));
        verifyNoInteractions(paymentMapper);
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
        riskDecision.setMessage("支付需要人工风控复核");

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
        ArgumentCaptor<String> eventPayloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).insertEvent(
                anyString(),
                eq("PAYMENT_RISK_REVIEW"),
                eq("PAY-RISK-001"),
                eq("ORD-RISK-001"),
                eventPayloadCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(
                eventPayloadCaptor.getValue().contains("支付需要人工风控复核"));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-RISK-001", result.getPrepayOrderNo());
    }

    @Test
    void shouldStopBeforePaymentWritesWhenRiskServiceRejectsItsResponse() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-RISK-INVALID-001");
        prepayOrder.setPaymentOrderId("PAY-RISK-INVALID-001");
        prepayOrder.setOrderNo("ORD-RISK-INVALID-001");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-RISK-INVALID-001");
        paymentDetail.setOrderNo("ORD-RISK-INVALID-001");
        paymentDetail.setStatus("PREPAY_CREATED");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentMapper.findPrepay("PRE-RISK-INVALID-001")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-RISK-INVALID-001")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE,
                        "风控中心返回结果非法，无法确认支付准入状态"));

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-INVALID-001");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService,
                        paymentEventDispatchService,
                        paymentRiskControlService,
                        paymentOpsConfigService)
                        .submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, exception.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying(anyString());
        verify(paymentMapper, never()).insertPaymentAttempt(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentChannelSubmitService, never()).ensureAdapterAvailable(anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldContinueSubmitAfterRiskReviewApproved() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-RISK-APPROVED-001");
        prepayOrder.setPaymentOrderId("PAY-RISK-APPROVED-001");
        prepayOrder.setOrderNo("ORD-RISK-APPROVED-001");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("6888.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-RISK-APPROVED-001");
        paymentDetail.setOrderNo("ORD-RISK-APPROVED-001");
        paymentDetail.setStatus("RISK_REVIEW");
        paymentDetail.setChannelTransactionNo("");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");

        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        riskDecision.setDecisionType("success");
        riskDecision.setReviewNo("REVIEW-APPROVED-001");
        riskDecision.setRiskTag("大额支付人工复核");
        riskDecision.setMessage("人工复核已通过，允许进入支付主链路");

        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-APPROVED-001");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");

        when(paymentMapper.findPrepay("PRE-RISK-APPROVED-001")).thenReturn(prepayOrder, prepayOrder);
        when(paymentMapper.findDetail("PAY-RISK-APPROVED-001")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-RISK-APPROVED-001")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-APPROVED-001");
        request.setPaymentMethod("微信支付");
        request.setMerchantNo("MCH_DEFAULT");
        request.setSourceAppId("default-app");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13900001111");

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
        verify(paymentMapper, times(1)).updatePrepayToPaying("PRE-RISK-APPROVED-001");
        verify(paymentChannelSubmitService, times(1)).submit(org.mockito.ArgumentMatchers.any(PaymentChannelSubmitRequestDTO.class));
        verify(paymentMapper, never()).updatePaymentStatus("PAY-RISK-APPROVED-001", "RISK_REVIEW", "warn", "");
        org.junit.jupiter.api.Assertions.assertEquals("PRE-RISK-APPROVED-001", result.getPrepayOrderNo());
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
        snapshot.setSnapshotVersion(12L);

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
        verify(paymentMapper, times(1)).updateRouteOpsConfigSnapshotVersion(anyString(), eq(12L));
        verify(paymentMapper, times(1)).updateAttemptOpsConfigSnapshotVersion(anyString(), eq(12L));
    }

    @Test
    void shouldRejectOversizedPaymentMethodFromOpsConfigBeforeRouting() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-OPS-INVALID-METHOD");
        prepayOrder.setPaymentOrderId("PAY-OPS-INVALID-METHOD");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-OPS-INVALID-METHOD");
        paymentDetail.setStatus("CREATED");

        PaymentOpsConfigSnapshotDTO snapshot = new PaymentOpsConfigSnapshotDTO();
        snapshot.setDefaultPayMethod(String.join("", Collections.nCopies(33, "M")));

        when(paymentMapper.findPrepay("PRE-OPS-INVALID-METHOD")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-OPS-INVALID-METHOD")).thenReturn(paymentDetail);
        when(paymentOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"))
                .thenReturn(snapshot);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-OPS-INVALID-METHOD");
        request.setChannelCode("wx_h5");
        request.setTerminal("APP_WEB");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService,
                        paymentEventDispatchService,
                        paymentRiskControlService,
                        paymentOpsConfigService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID,
                exception.getCode());
        verify(paymentOpsConfigService).loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP");
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(paymentRiskControlService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectSubmitWhenOpsConfigSnapshotMissingAndRequestNeedsDefaults() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-OPS-MISSING-001");
        prepayOrder.setPaymentOrderId("PAY-OPS-MISSING-001");
        prepayOrder.setOrderNo("ORD-OPS-MISSING-001");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("268.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-OPS-MISSING-001");
        paymentDetail.setOrderNo("ORD-OPS-MISSING-001");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        when(paymentMapper.findPrepay("PRE-OPS-MISSING-001")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-OPS-MISSING-001")).thenReturn(paymentDetail);
        when(paymentOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(null);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-OPS-MISSING-001");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService,
                        paymentEventDispatchService,
                        paymentRiskControlService,
                        paymentOpsConfigService)
                        .submit(request)
        );
        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals("当前支付场景缺少有效运营配置，无法补齐支付方式或渠道", exception.getMessage());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectSubmitWhenOpsConfigSnapshotMissesDefaultPaymentMethod() {
        PrepayOrderDTO prepayOrder = new PrepayOrderDTO();
        prepayOrder.setPrepayOrderNo("PRE-OPS-INCOMPLETE-001");
        prepayOrder.setPaymentOrderId("PAY-OPS-INCOMPLETE-001");
        prepayOrder.setOrderNo("ORD-OPS-INCOMPLETE-001");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("268.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-OPS-INCOMPLETE-001");
        paymentDetail.setOrderNo("ORD-OPS-INCOMPLETE-001");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        PaymentOpsConfigSnapshotDTO snapshot = new PaymentOpsConfigSnapshotDTO();
        snapshot.setBusinessCode("HOME_CLEAN");
        snapshot.setPayType("PAY_CONSUME");
        snapshot.setTerminalType("APP");
        snapshot.setPrimaryChannelProfileCode("CHANNEL_WX_H5");

        when(paymentMapper.findPrepay("PRE-OPS-INCOMPLETE-001")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-OPS-INCOMPLETE-001")).thenReturn(paymentDetail);
        when(paymentOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(snapshot);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-OPS-INCOMPLETE-001");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService,
                        paymentEventDispatchService,
                        paymentRiskControlService,
                        paymentOpsConfigService)
                        .submit(request)
        );
        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals("当前支付场景未配置默认支付方式，无法提交支付", exception.getMessage());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    private PrepayRequestDTO validPrepayRequest() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORDER-001");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("CUSTOMER");
        request.setCashierTitle("CASHIER");
        return request;
    }

    private PaymentListQueryDTO paymentListQuery(
            String paymentOrderId,
            String orderNo,
            String channelCode,
            String paymentMethod,
            String status) {
        PaymentListQueryDTO query = new PaymentListQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        query.setOrderNo(orderNo);
        query.setChannelCode(channelCode);
        query.setPaymentMethod(paymentMethod);
        query.setStatus(status);
        return query;
    }

    private PaymentSubmitRequestDTO validSubmitRequest() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-001");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setSourceAppId("cashier-app");
        request.setMerchantNo("MCH-001");
        request.setAccessToken("TOKEN-001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setClientDeviceId("DEVICE-001");
        request.setPayerPhone("13800138000");
        return request;
    }

    private void assertSubmitRequestInvalid(
            PaymentServiceImpl service,
            PaymentSubmitRequestDTO request) {
        BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(request));
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
    }

    private void assertPaymentRequestInvalid(org.junit.jupiter.api.function.Executable action) {
        BusinessException exception = assertThrows(BusinessException.class, action);
        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_REQUEST_INVALID, exception.getCode());
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
