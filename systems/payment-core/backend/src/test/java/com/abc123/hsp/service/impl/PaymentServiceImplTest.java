package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.abc123.hsp.dto.CashierPageDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentSubmitConcurrencyTokenDTO;
import com.abc123.hsp.dto.PaymentAttemptIdempotencyDTO;
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
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
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

    private PrepayOrderDTO boundPrepay() {
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayAmountBindingValid(true);
        prepay.setActivePrepayCandidateCount(1);
        prepay.setOrderNo("ORD-DEFAULT");
        prepay.setSourceKind("VIRTUAL");
        return prepay;
    }

    @BeforeEach
    void defaultPrepayStatusUpdate() {
        org.mockito.Mockito.lenient()
                .when(paymentMapper.lockPrepayCreation(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updatePrepayStatusByPaymentOrderId(
                        anyString(), anyString(), anyString()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updatePaymentMethodAndChannel(
                        anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updatePaymentReturnCodeSnapshot(
                        anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updatePaymentRiskStatus(
                        anyString(), anyString(), anyString()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updateRouteOpsConfigSnapshotVersion(
                        anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.updateAttemptOpsConfigSnapshotVersion(
                        anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertPaymentAttempt(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertRouteRecord(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertPaymentProcessLog(
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertNotifyLog(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertEvent(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertBill(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertPaymentOrder(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertPrepayOrder(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.insertSubmitConcurrencyToken(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(1);
    }

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
        PrepayRequestDTO remarkRequest = validPrepayRequest();
        remarkRequest.setRemark("REMARK\n");
        PrepayRequestDTO oversizedOrderNoRequest = validPrepayRequest();
        oversizedOrderNoRequest.setOrderNo(repeat("O", 64) + " ");

        assertPaymentRequestInvalid(() -> service.detail("PAY-DETAIL\n"));
        assertPaymentRequestInvalid(() -> service.cashier("PRE-001\n"));
        assertPaymentRequestInvalid(() -> service.query(queryRequest));
        assertPaymentRequestInvalid(() -> service.close(closeRequest));
        assertPaymentRequestInvalid(() -> service.prepay(orderNoRequest));
        assertPaymentRequestInvalid(() -> service.prepay(paySceneRequest));
        assertPaymentRequestInvalid(() -> service.prepay(customerNameRequest));
        assertPaymentRequestInvalid(() -> service.prepay(cashierTitleRequest));
        assertPaymentRequestInvalid(() -> service.prepay(remarkRequest));
        assertPaymentRequestInvalid(() -> service.prepay(oversizedOrderNoRequest));

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
        PaymentSubmitRequestDTO oversizedPaymentMethodRequest = validSubmitRequest();
        oversizedPaymentMethodRequest.setPaymentMethod(repeat("M", 32) + " ");

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
        assertSubmitRequestInvalid(service, oversizedPaymentMethodRequest);
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
    void shouldNormalizeSupportedMiniProgramTerminalsBeforeLoadingPrepay() {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);

        for (String terminal : Arrays.asList("MINI_PROGRAM", "WECHAT_MINI_PROGRAM", "WX_MINI_PROGRAM", "小程序")) {
            PaymentSubmitRequestDTO request = validSubmitRequest();
            request.setPrepayOrderNo("PRE-MINI-" + terminal);
            request.setTerminal(terminal);

            BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(request));

            org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, exception.getCode());
            org.junit.jupiter.api.Assertions.assertEquals("MINI_PROGRAM", request.getTerminal());
        }
        verify(paymentMapper, times(4)).findPrepay(org.mockito.ArgumentMatchers.startsWith("PRE-MINI-"));
        verifyNoInteractions(paymentChannelRoutingService, paymentRiskControlService, paymentChannelSubmitService);
    }

    @Test
    void shouldAcceptApiTerminalBeforeLoadingPrepay() {
        PaymentSubmitRequestDTO request = validSubmitRequest();
        request.setPrepayOrderNo("PRE-API-TERMINAL");
        request.setTerminal(" api ");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals("API", request.getTerminal());
        verify(paymentMapper).findPrepay("PRE-API-TERMINAL");
        verifyNoInteractions(paymentChannelRoutingService, paymentRiskControlService, paymentChannelSubmitService);
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
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("VIRTUAL");
        detail.setPaymentOrderId("PAY-001");
        detail.setOrderNo("ORD-CALLBACK-VIRTUAL-001");
        detail.setChannel("wx_h5");
        detail.setAmount("¥168.00");
        detail.setStatus("SUCCESS");
        detail.setStatusType("success");
        detail.setChannelTransactionNo("CHANNEL-002");
        detail.setCreatedAt("2026-09-12 09:59:00");
        detail.setSuccessAt("2026-09-12 10:00:00");
        detail.setLatestAttemptStatus("成功");
        detail.setLatestAttemptStatusType("success");
        detail.setLatestAttemptCompletedAt("2026-09-12 10:00:00");
        detail.setPaymentFactTimelineValid(true);
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

        verify(paymentMapper, never()).updatePaymentRiskStatus(
                eq("PAY-001"), anyString(), anyString());
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
    void shouldRejectRepeatedSuccessCallbackWhenPaymentFactsAreIncomplete() {
        PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-CALLBACK", "SUCCESS");
        detail.setLatestAttemptStatus("处理中");
        when(paymentMapper.findDetail("PAY-INCONSISTENT-CALLBACK")).thenReturn(detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-INCONSISTENT-CALLBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-QUERY-001");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessCallbackBeforeSettlementWhenAttemptChannelDiffers() {
        PaymentDetailDTO detail = queryDetail("PAY-WRONG-SUBMIT-CHANNEL", "WAIT_CALLBACK");
        detail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail("PAY-WRONG-SUBMIT-CHANNEL")).thenReturn(detail);
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-WRONG-SUBMIT-CHANNEL");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-WRONG-CHANNEL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRejectChannelQueryBeforeExternalCallWhenAttemptChannelDiffers() {
        PaymentDetailDTO detail = queryDetail("PAY-WRONG-QUERY-CHANNEL", "WAIT_CALLBACK");
        detail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail("PAY-WRONG-QUERY-CHANNEL")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-WRONG-QUERY-CHANNEL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenAttemptCompletionTimeIsMissing() {
        PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-QUERY", "SUCCESS");
        detail.setLatestAttemptCompletedAt(null);
        when(paymentMapper.findDetail("PAY-INCONSISTENT-QUERY")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-INCONSISTENT-QUERY");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenAttemptStatusTypeIsNotSuccess() {
        for (String attemptStatusType : Arrays.asList(null, "warn")) {
            PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-ATTEMPT-TYPE", "SUCCESS");
            detail.setLatestAttemptStatusType(attemptStatusType);
            when(paymentMapper.findDetail("PAY-INCONSISTENT-ATTEMPT-TYPE")).thenReturn(detail);
            com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
            request.setPaymentOrderId("PAY-INCONSISTENT-ATTEMPT-TYPE");

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    new PaymentServiceImpl(
                            paymentMapper,
                            paymentCallbackSignatureService,
                            paymentChannelRoutingService,
                            paymentChannelQueryService,
                            paymentChannelSubmitService)
                            .query(request));

            org.junit.jupiter.api.Assertions.assertEquals(
                    "payment success facts are inconsistent", exception.getMessage());
        }
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldReturnSuccessfulPaymentQueryWithoutCallingChannel() {
        PaymentDetailDTO detail = queryDetail("PAY-COMPLETE-QUERY", "SUCCESS");
        when(paymentMapper.findDetail("PAY-COMPLETE-QUERY")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-COMPLETE-QUERY")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-COMPLETE-QUERY")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-COMPLETE-QUERY")).thenReturn(Collections.emptyList());
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-COMPLETE-QUERY");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .query(request);

        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        verifyNoInteractions(paymentChannelQueryService, paymentChannelQueryRateLimitService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenSuccessTimeIsMissing() {
        PaymentDetailDTO detail = queryDetail("PAY-MISSING-SUCCESS-TIME", "SUCCESS");
        detail.setSuccessAt(null);
        when(paymentMapper.findDetail("PAY-MISSING-SUCCESS-TIME")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING-SUCCESS-TIME");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenCreationTimeIsMissing() {
        PaymentDetailDTO detail = queryDetail("PAY-MISSING-CREATED-TIME", "SUCCESS");
        detail.setCreatedAt(null);
        when(paymentMapper.findDetail("PAY-MISSING-CREATED-TIME")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING-CREATED-TIME");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenCreationTimeFollowsSuccessFacts() {
        PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-CREATED-TIME", "SUCCESS");
        detail.setCreatedAt("2026-09-12 10:01:00");
        when(paymentMapper.findDetail("PAY-INCONSISTENT-CREATED-TIME")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-INCONSISTENT-CREATED-TIME");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenAttemptCompletionFollowsSuccessTime() {
        PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-TIME", "SUCCESS");
        detail.setLatestAttemptCompletedAt("2026-09-12 10:01:00");
        detail.setSuccessAt("2026-09-12 10:00:00");
        when(paymentMapper.findDetail("PAY-INCONSISTENT-TIME")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-INCONSISTENT-TIME");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWhenRawTimesAreInvertedWithinSameSecond() {
        PaymentDetailDTO detail = queryDetail("PAY-INCONSISTENT-FRACTIONAL", "SUCCESS");
        detail.setPaymentFactTimelineValid(false);
        when(paymentMapper.findDetail("PAY-INCONSISTENT-FRACTIONAL")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-INCONSISTENT-FRACTIONAL");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWithoutLatestAttempt() {
        PaymentDetailDTO detail = queryDetail("PAY-MISSING-LATEST-ATTEMPT", "SUCCESS");
        detail.setLatestAttemptStatus(null);
        detail.setLatestAttemptCompletedAt(null);
        when(paymentMapper.findDetail("PAY-MISSING-LATEST-ATTEMPT")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING-LATEST-ATTEMPT");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWithoutChannelBinding() {
        PaymentDetailDTO detail = queryDetail("PAY-MISSING-CHANNEL-BINDING", "SUCCESS");
        detail.setChannel(null);
        when(paymentMapper.findDetail("PAY-MISSING-CHANNEL-BINDING")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING-CHANNEL-BINDING");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectSuccessfulPaymentQueryWithoutChannelTransaction() {
        PaymentDetailDTO detail = queryDetail("PAY-MISSING-CHANNEL-TRANSACTION", "SUCCESS");
        detail.setChannelTransactionNo(null);
        when(paymentMapper.findDetail("PAY-MISSING-CHANNEL-TRANSACTION")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-MISSING-CHANNEL-TRANSACTION");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .query(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment success facts are inconsistent", exception.getMessage());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldSettleLateSuccessCallbackAfterLegacyExpiryClose() {
        PaymentDetailDTO closedDetail = queryDetail("PAY-EXPIRED-CALLBACK", "CLOSED");
        closedDetail.setLatestAttemptStatus("待回调");
        PaymentDetailDTO settledDetail = queryDetail("PAY-EXPIRED-CALLBACK", "SUCCESS");
        closedDetail.setSourceKind("STANDARD_ORDER");
        settledDetail.setSourceKind("STANDARD_ORDER");
        closedDetail.setCustomerName("张女士");
        settledDetail.setCustomerName("张女士");
        when(paymentMapper.findDetail("PAY-EXPIRED-CALLBACK"))
                .thenReturn(closedDetail, closedDetail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-EXPIRED-CALLBACK", "WX-EXPIRED-CALLBACK")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-EXPIRED-CALLBACK")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-QUERY-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-QUERY-001")).thenReturn("张女士");
        when(paymentMapper.updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士"))
                .thenReturn(1);
        when(paymentMapper.updateBillAfterPayment(
                "BILL-QUERY-001", new BigDecimal("168.00"), "已结清", "success",
                "ORD-QUERY-001", "张女士", BigDecimal.ZERO)).thenReturn(1);
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
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士");
        verify(paymentMapper).updateBillAfterPayment(
                "BILL-QUERY-001", new BigDecimal("168.00"), "已结清", "success",
                "ORD-QUERY-001", "张女士", BigDecimal.ZERO);
        verify(paymentMapper).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), eq("PAY-EXPIRED-CALLBACK"),
                eq("ORD-QUERY-001"), eq("{\"channel\":\"wx_h5\",\"payScene\":\"HOME_CLEAN\"}"));
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
    }

    @Test
    void shouldRejectSuccessCallbackWhenClosedPaymentCannotRecover() {
        PaymentDetailDTO closedDetail = queryDetail("PAY-CALLBACK-CLOSED-CONFLICT", "CLOSED");
        closedDetail.setLatestAttemptStatus("结果未知，待查单");
        when(paymentMapper.findDetail("PAY-CALLBACK-CLOSED-CONFLICT"))
                .thenReturn(closedDetail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-CLOSED-CONFLICT");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-CLOSED-CONFLICT");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals("payment callback success could not recover closed payment", exception.getMessage());
        verify(paymentMapper).markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-CLOSED-CONFLICT", "WX-CALLBACK-CLOSED-CONFLICT");
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                eq("PAY-CALLBACK-CLOSED-CONFLICT"), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                eq("PAY-CALLBACK-CLOSED-CONFLICT"), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessCallbackForClosedPaymentWithTerminalLatestAttempt() {
        PaymentDetailDTO detail = queryDetail("PAY-CLOSED-TERMINAL-CALLBACK", "CLOSED");
        detail.setLatestAttemptStatus("SUCCESS");
        when(paymentMapper.findDetail("PAY-CLOSED-TERMINAL-CALLBACK")).thenReturn(detail);
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CLOSED-TERMINAL-CALLBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CLOSED-TERMINAL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessfulCallbackWithoutPaymentAmountBeforeStateMutation() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-MISSING-AMOUNT", "WAIT_CALLBACK");
        detail.setAmount(null);
        when(paymentMapper.findDetail("PAY-CALLBACK-MISSING-AMOUNT"))
                .thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001"))
                .thenReturn("ORD-QUERY-001");

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-MISSING-AMOUNT");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-MISSING-AMOUNT");

        assertThrows(IllegalArgumentException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("wx_h5", callback));

        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(
                anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldRejectCallbackSuccessWhenBoundPrepayAmountDiffers() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-AMOUNT-MISMATCH", "WAIT_CALLBACK");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-CALLBACK-AMOUNT-MISMATCH")).thenReturn(detail, detail);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-AMOUNT-MISMATCH");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-AMOUNT-MISMATCH");

        BusinessException error = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, error.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotAcknowledgeSuccessCallbackWhenAtomicSettlementLosesAmountBinding() {
        PaymentDetailDTO pending = queryDetail("PAY-CALLBACK-ATOMIC-MISMATCH", "WAIT_CALLBACK");
        PaymentDetailDTO drifted = queryDetail("PAY-CALLBACK-ATOMIC-MISMATCH", "WAIT_CALLBACK");
        drifted.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-CALLBACK-ATOMIC-MISMATCH"))
                .thenReturn(pending, pending, drifted);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-ATOMIC-MISMATCH");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-ATOMIC-MISMATCH");
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals("payment success confirmation was not applied atomically", error.getMessage());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessCallbackWhenAtomicWinnerUsesAnotherTransaction() {
        PaymentDetailDTO pending = queryDetail("PAY-CALLBACK-ATOMIC-TXN", "WAIT_CALLBACK");
        PaymentDetailDTO winner = queryDetail("PAY-CALLBACK-ATOMIC-TXN", "SUCCESS");
        winner.setChannelTransactionNo("WX-OTHER-TRANSACTION");
        when(paymentMapper.findDetail("PAY-CALLBACK-ATOMIC-TXN"))
                .thenReturn(pending, pending, winner);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-ATOMIC-TXN");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-EXPECTED");
        BusinessException error = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals(ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH, error.getCode());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                eq("PAY-CALLBACK-ATOMIC-TXN"), anyString());
    }

    @Test
    void shouldRejectActiveQuerySuccessWhenBoundPrepayAmountDiffers() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-AMOUNT-MISMATCH", "WAIT_CALLBACK");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-QUERY-AMOUNT-MISMATCH")).thenReturn(detail, detail);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-AMOUNT-MISMATCH");
        BusinessException error = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, error.getCode());
        verifyNoInteractions(paymentChannelQueryRateLimitService, paymentChannelQueryService);
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldNotReportSuccessQueryWhenAtomicSettlementLosesAmountBinding() {
        PaymentDetailDTO pending = queryDetail("PAY-QUERY-ATOMIC-MISMATCH", "WAIT_CALLBACK");
        PaymentDetailDTO drifted = queryDetail("PAY-QUERY-ATOMIC-MISMATCH", "WAIT_CALLBACK");
        drifted.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-QUERY-ATOMIC-MISMATCH"))
                .thenReturn(pending, pending, drifted);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(pending))
                .thenReturn(queryResult("SUCCESS", "WX-ATOMIC-MISMATCH", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-ATOMIC-MISMATCH");
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals("payment success confirmation was not applied atomically", error.getMessage());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessQueryWhenAtomicWinnerUsesAnotherTransaction() {
        PaymentDetailDTO pending = queryDetail("PAY-QUERY-ATOMIC-TXN", "WAIT_CALLBACK");
        PaymentDetailDTO winner = queryDetail("PAY-QUERY-ATOMIC-TXN", "SUCCESS");
        winner.setChannelTransactionNo("WX-OTHER-TRANSACTION");
        when(paymentMapper.findDetail("PAY-QUERY-ATOMIC-TXN"))
                .thenReturn(pending, pending, winner);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(pending))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-EXPECTED", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-ATOMIC-TXN");
        BusinessException error = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, error.getCode());
        assertEquals("channel query transaction does not match payment transaction", error.getMessage());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-ATOMIC-TXN"), eq("wx_h5"), eq("QUERY_FAILED"),
                anyString(), anyString(), eq("查单失败"), eq("danger"));
    }

    @Test
    void shouldRejectHistoricalSuccessWhenBoundPrepayAmountDiffers() {
        PaymentDetailDTO detail = queryDetail("PAY-SUCCESS-AMOUNT-MISMATCH", "SUCCESS");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-SUCCESS-AMOUNT-MISMATCH")).thenReturn(detail);

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).detail("PAY-SUCCESS-AMOUNT-MISMATCH"));
        assertEquals("payment success facts are inconsistent", error.getMessage());
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
        detail.setPrepayAmountBindingValid(true);
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
        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectCallbackForDifferentChannelTransaction() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
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
        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
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
        detail.setPrepayAmountBindingValid(true);
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
        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
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
    void shouldRejectCashierPrepayOrderWithoutPaymentBinding() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-UNBOUND");
        when(paymentMapper.findPrepay("PRE-CASHIER-UNBOUND")).thenReturn(prepay);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .cashier("PRE-CASHIER-UNBOUND"));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PREPAY_ORDER_NOT_FOUND, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "预付单不存在或未绑定支付单", exception.getMessage());
        verify(paymentMapper, never()).findSubmissionFact(anyString());
    }

    @Test
    void shouldExposePaymentOrderIdOnCashierPage() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-001");
        prepay.setPaymentOrderId("PAY-CASHIER-001");
        when(paymentMapper.findPrepay("PRE-CASHIER-001")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setPaymentOrderId("PAY-CASHIER-001");
        submissionFact.setStatus("WAIT_CALLBACK");
        submissionFact.setChannel("wx_h5");
        submissionFact.setPaymentSubmissionChannelValid(true);
        submissionFact.setLatestAttemptStatus("处理中");
        submissionFact.setLatestAttemptStatusType("info");
        submissionFact.setLatestAttemptCompletedAt(null);
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-001")).thenReturn(submissionFact);

        com.abc123.hsp.dto.CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-001");

        org.junit.jupiter.api.Assertions.assertEquals("PAY-CASHIER-001", result.getPaymentOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("CHAIN-PAY-CASHIER-001", result.getBusinessChainId());
        org.junit.jupiter.api.Assertions.assertEquals("WAIT_CALLBACK", result.getPaymentStatus());
        org.junit.jupiter.api.Assertions.assertEquals("待回调", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("warn", result.getStatusType());
        org.junit.jupiter.api.Assertions.assertEquals("处理中", result.getLatestAttemptStatus());
        org.junit.jupiter.api.Assertions.assertEquals("info", result.getLatestAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertNull(result.getLatestAttemptCompletedAt());
        org.junit.jupiter.api.Assertions.assertNull(result.getReviewPaymentMethod());
        org.junit.jupiter.api.Assertions.assertNull(result.getReviewChannelCode());
        org.junit.jupiter.api.Assertions.assertNull(result.getChannelTransactionNo());
    }

    @Test
    void shouldExposeOriginalReviewMethodAndChannelOnlyWhenPaymentIsUnderReview() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-REVIEW");
        prepay.setPaymentOrderId("PAY-CASHIER-REVIEW");
        when(paymentMapper.findPrepay("PRE-CASHIER-REVIEW")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setStatus("RISK_REVIEW");
        submissionFact.setPaymentMethod("微信支付");
        submissionFact.setChannel("WX_H5");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-REVIEW")).thenReturn(submissionFact);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-REVIEW");

        assertEquals("RISK_REVIEW", result.getPaymentStatus());
        assertEquals("待风控复核", result.getStatus());
        assertEquals("微信支付", result.getReviewPaymentMethod());
        assertEquals("WX_H5", result.getReviewChannelCode());
        org.junit.jupiter.api.Assertions.assertNull(result.getChannelTransactionNo());

        submissionFact.setChannelTransactionNo("WX-STALE");
        CashierPageDTO anomalous = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-REVIEW");
        assertEquals("WX-STALE", anomalous.getChannelTransactionNo());
    }

    @Test
    void shouldRejectWaitingCashierWhenSubmissionFactIsMissing() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-PENDING");
        prepay.setPaymentOrderId("PAY-CASHIER-PENDING");
        prepay.setPaymentStatus("WAIT_CALLBACK");
        prepay.setCashierStatus("待支付");
        prepay.setCashierStatusType("warn");
        when(paymentMapper.findPrepay("PRE-CASHIER-PENDING")).thenReturn(prepay);
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-PENDING")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).cashier("PRE-CASHIER-PENDING"));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
    }

    @Test
    void shouldRejectWaitingCashierWhenSubmissionAttemptIsIneligible() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-INVALID-FACT");
        prepay.setPaymentOrderId("PAY-CASHIER-INVALID-FACT");
        prepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay("PRE-CASHIER-INVALID-FACT")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setPaymentOrderId("PAY-CASHIER-INVALID-FACT");
        submissionFact.setStatus("WAIT_CALLBACK");
        submissionFact.setChannel("wx_h5");
        submissionFact.setLatestAttemptStatus("待回调");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-INVALID-FACT")).thenReturn(submissionFact);
        PaymentServiceImpl service = new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                paymentChannelRoutingService, paymentChannelQueryService, paymentChannelSubmitService);

        submissionFact.setPaymentSubmissionChannelValid(false);
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.cashier("PRE-CASHIER-INVALID-FACT")).getCode());
        submissionFact.setPaymentSubmissionChannelValid(true);
        submissionFact.setLatestAttemptStatus("失败");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.cashier("PRE-CASHIER-INVALID-FACT")).getCode());
        submissionFact.setLatestAttemptStatus("待回调");
        submissionFact.setLatestAttemptCompletedAt("2026-09-12 10:00:00");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.cashier("PRE-CASHIER-INVALID-FACT")).getCode());
        submissionFact.setLatestAttemptCompletedAt(null);
        submissionFact.setPaymentSubmissionChannelValid(null);
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.cashier("PRE-CASHIER-INVALID-FACT")).getCode());
    }

    @Test
    void shouldRejectCashierSuccessWhenRawPaymentTimelineIsInverted() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-SUCCESS");
        prepay.setPaymentOrderId("PAY-CASHIER-SUCCESS");
        prepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CASHIER-SUCCESS")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setStatus("SUCCESS");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-SUCCESS")).thenReturn(submissionFact);
        PaymentDetailDTO detail = queryDetail("PAY-CASHIER-SUCCESS", "SUCCESS");
        detail.setPaymentFactTimelineValid(false);
        when(paymentMapper.findDetail("PAY-CASHIER-SUCCESS")).thenReturn(detail);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .cashier("PRE-CASHIER-SUCCESS"));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
    }

    @Test
    void shouldRejectCashierSuccessWhenSubmissionFactIsMissing() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-MISSING");
        prepay.setPaymentOrderId("PAY-CASHIER-MISSING");
        prepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CASHIER-MISSING")).thenReturn(prepay);
        when(paymentMapper.findDetail("PAY-CASHIER-MISSING")).thenReturn(null);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .cashier("PRE-CASHIER-MISSING"));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
    }

    @Test
    void shouldExposeCashierSuccessWhenPaymentFactsAreConsistent() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-VALID");
        prepay.setPaymentOrderId("PAY-CASHIER-VALID");
        prepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CASHIER-VALID")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setStatus("SUCCESS");
        submissionFact.setLatestAttemptStatus("成功");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-VALID")).thenReturn(submissionFact);
        when(paymentMapper.findDetail("PAY-CASHIER-VALID"))
                .thenReturn(queryDetail("PAY-CASHIER-VALID", "SUCCESS"));

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-VALID");

        assertEquals("SUCCESS", result.getPaymentStatus());
        assertEquals("支付成功", result.getStatus());
        assertEquals("success", result.getStatusType());
        assertEquals("wx_h5", result.getChannel());
        assertEquals("2026-09-12 10:00:00", result.getLatestAttemptCompletedAt());
    }

    @Test
    void shouldRejectCashierSuccessWhenDetailBelongsToAnotherPayment() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-MISMATCH");
        prepay.setPaymentOrderId("PAY-CASHIER-MISMATCH");
        prepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CASHIER-MISMATCH")).thenReturn(prepay);
        when(paymentMapper.findDetail("PAY-CASHIER-MISMATCH"))
                .thenReturn(queryDetail("PAY-OTHER", "SUCCESS"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .cashier("PRE-CASHIER-MISMATCH"));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
    }

    @Test
    void shouldExposeCashierSuccessFromPrepayStatusWithoutSubmissionFact() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-FALLBACK");
        prepay.setPaymentOrderId("PAY-CASHIER-FALLBACK");
        prepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CASHIER-FALLBACK")).thenReturn(prepay);
        when(paymentMapper.findDetail("PAY-CASHIER-FALLBACK"))
                .thenReturn(queryDetail("PAY-CASHIER-FALLBACK", "SUCCESS"));

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-FALLBACK");

        assertEquals("支付成功", result.getStatus());
        assertEquals("成功", result.getLatestAttemptStatus());
        assertEquals("2026-09-12 10:00:00", result.getLatestAttemptCompletedAt());
    }

    @Test
    void shouldExposePrepayCreatedStatusBeforeAnyPaymentAttempt() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-UNSUBMITTED");
        prepay.setPaymentOrderId("PAY-CASHIER-UNSUBMITTED");
        prepay.setCashierStatus("待支付");
        prepay.setCashierStatusType("warn");
        when(paymentMapper.findPrepay("PRE-CASHIER-UNSUBMITTED")).thenReturn(prepay);
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-UNSUBMITTED")).thenReturn(null);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-UNSUBMITTED");

        org.junit.jupiter.api.Assertions.assertEquals("PREPAY_CREATED", result.getPaymentStatus());
        org.junit.jupiter.api.Assertions.assertNull(result.getLatestAttemptStatus());
    }

    @Test
    void shouldMarkCashierExpiredWhenUnsubmittedPrepayHasPassedExpiry() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-EXPIRED");
        prepay.setPaymentOrderId("PAY-CASHIER-EXPIRED");
        prepay.setCashierStatus("待支付");
        when(paymentMapper.findPrepay("PRE-CASHIER-EXPIRED")).thenReturn(prepay);
        when(paymentMapper.isPrepayExpired("PRE-CASHIER-EXPIRED")).thenReturn(true);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-EXPIRED");

        org.junit.jupiter.api.Assertions.assertEquals("已失效", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("danger", result.getStatusType());
        verify(paymentMapper).isPrepayExpired("PRE-CASHIER-EXPIRED");
    }

    @Test
    void shouldNotMarkSubmittedCashierExpiredAfterPrepayExpiry() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-WAITING");
        prepay.setPaymentOrderId("PAY-CASHIER-WAITING");
        prepay.setPaymentStatus("PREPAY_CREATED");
        prepay.setCashierStatus("待支付");
        prepay.setCashierStatusType("warn");
        when(paymentMapper.findPrepay("PRE-CASHIER-WAITING")).thenReturn(prepay);
        PaymentDetailDTO submissionFact = new PaymentDetailDTO();
        submissionFact.setPaymentOrderId("PAY-CASHIER-WAITING");
        submissionFact.setStatus("WAIT_CALLBACK");
        submissionFact.setChannel("wx_h5");
        submissionFact.setPaymentMethod("微信支付");
        submissionFact.setChannelTransactionNo("WX-CASHIER-WAITING");
        submissionFact.setPaymentSubmissionChannelValid(true);
        submissionFact.setLatestAttemptStatus("处理中");
        submissionFact.setLatestAttemptStatusType("info");
        when(paymentMapper.findSubmissionFact("PAY-CASHIER-WAITING")).thenReturn(submissionFact);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-WAITING");

        org.junit.jupiter.api.Assertions.assertEquals("WAIT_CALLBACK", result.getPaymentStatus());
        org.junit.jupiter.api.Assertions.assertEquals("待回调", result.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("warn", result.getStatusType());
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", result.getChannel());
        org.junit.jupiter.api.Assertions.assertEquals("微信支付", result.getPaymentMethod());
        org.junit.jupiter.api.Assertions.assertEquals("WX-CASHIER-WAITING", result.getChannelTransactionNo());
        verify(paymentMapper, never()).isPrepayExpired("PRE-CASHIER-WAITING");
    }

    @Test
    void shouldFilterCashierChannelsByKnownTerminal() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-PC");
        prepay.setPaymentOrderId("PAY-CASHIER-PC");
        when(paymentMapper.findPrepay("PRE-CASHIER-PC")).thenReturn(prepay);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-PC", " pc_web ");

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Arrays.asList("支付宝", "银行卡"), result.getChannels());
    }

    @Test
    void shouldUseActiveSourcePolicyWhenCashierChannelsAreRestricted() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-POLICY");
        prepay.setPaymentOrderId("PAY-CASHIER-POLICY");
        when(paymentMapper.findPrepay("PRE-CASHIER-POLICY")).thenReturn(prepay);
        PaymentControlPolicyDTO policy = new PaymentControlPolicyDTO();
        policy.setAllowedPaymentMethods("支付宝");
        policy.setStrictMode("关闭");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-pc-web"))
                .thenReturn(policy);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-POLICY", "PC_WEB");

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Arrays.asList("支付宝"), result.getChannels());
    }

    @Test
    void shouldHideCashierChannelsWhenStrictPolicySelfCheckIsNotPassing() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-SELF-CHECK");
        prepay.setPaymentOrderId("PAY-CASHIER-SELF-CHECK");
        when(paymentMapper.findPrepay("PRE-CASHIER-SELF-CHECK")).thenReturn(prepay);
        PaymentControlPolicyDTO policy = new PaymentControlPolicyDTO();
        policy.setAllowedPaymentMethods("支付宝,银行卡");
        policy.setStrictMode("开启");
        policy.setSelfCheckStatus("WARNING");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-pc-web"))
                .thenReturn(policy);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-SELF-CHECK", "PC_WEB");

        org.junit.jupiter.api.Assertions.assertTrue(result.getChannels().isEmpty());

        for (String invalidSwitchValue : new String[]{"开启 ", null}) {
            policy.setStrictMode(invalidSwitchValue);
            CashierPageDTO invalidResult = new PaymentServiceImpl(
                    paymentMapper,
                    paymentCallbackSignatureService,
                    paymentChannelRoutingService,
                    paymentChannelQueryService,
                    paymentChannelSubmitService)
                    .cashier("PRE-CASHIER-SELF-CHECK", "PC_WEB");
            org.junit.jupiter.api.Assertions.assertTrue(invalidResult.getChannels().isEmpty());
        }
        policy.setStrictMode("开启");
        policy.setSelfCheckStatus("pass");
        CashierPageDTO aliasedSelfCheck = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-SELF-CHECK", "PC_WEB");
        org.junit.jupiter.api.Assertions.assertTrue(aliasedSelfCheck.getChannels().isEmpty());
    }

    @Test
    void shouldKeepLegacyCashierChannelsWhenTerminalIsUnknown() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CASHIER-LEGACY");
        prepay.setPaymentOrderId("PAY-CASHIER-LEGACY");
        when(paymentMapper.findPrepay("PRE-CASHIER-LEGACY")).thenReturn(prepay);

        CashierPageDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .cashier("PRE-CASHIER-LEGACY", "TABLET");

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Arrays.asList("微信支付", "支付宝", "银行卡"), result.getChannels());
    }

    @Test
    void shouldReuseActivePrepayWhenOrderIsStillOpen() {
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setSourceKind("STANDARD_ORDER");
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
        PaymentDetailDTO submissionFact = queryDetail("PAY-001", "WAIT_CALLBACK");
        submissionFact.setLatestAttemptStatus("待回调");
        submissionFact.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findSubmissionFact("PAY-001")).thenReturn(submissionFact);

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
        prepayOrder.verify(paymentMapper).findSubmissionFact("PAY-001");
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-001", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-001", result.getPaymentOrderId());
    }

    @Test
    void shouldRejectWaitingPrepayReuseWithoutChannelSubmissionFact() {
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setSourceKind("STANDARD_ORDER");
        activePrepay.setPrepayOrderNo("PRE-MISSING-FACT");
        activePrepay.setPaymentOrderId("PAY-MISSING-FACT");
        activePrepay.setPaymentStatus("WAIT_CALLBACK");
        activePrepay.setPayScene("HOME_CLEAN");
        activePrepay.setCustomerName("张女士");
        activePrepay.setAmount("¥168.00");
        activePrepay.setCashierTitle("家政服务收银台");
        when(paymentMapper.findOrderAmount("ORD-MISSING-FACT")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-MISSING-FACT")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-MISSING-FACT")).thenReturn("张女士");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-MISSING-FACT")).thenReturn(activePrepay);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-MISSING-FACT");
        request.setPayScene("HOME_CLEAN");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentMapper).findSubmissionFact("PAY-MISSING-FACT");
        verify(paymentMapper, never()).insertPaymentOrder(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectWaitingPrepayReuseWhenLatestAttemptContradictsPayment() {
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setSourceKind("STANDARD_ORDER");
        activePrepay.setPrepayOrderNo("PRE-CONFLICTING-FACT");
        activePrepay.setPaymentOrderId("PAY-CONFLICTING-FACT");
        activePrepay.setPaymentStatus("WAIT_CALLBACK");
        activePrepay.setPayScene("HOME_CLEAN");
        activePrepay.setCustomerName("张女士");
        activePrepay.setAmount("¥168.00");
        activePrepay.setCashierTitle("家政服务收银台");
        when(paymentMapper.findOrderAmount("ORD-CONFLICTING-FACT")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-CONFLICTING-FACT")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-CONFLICTING-FACT")).thenReturn("张女士");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-CONFLICTING-FACT")).thenReturn(activePrepay);
        PaymentDetailDTO submissionFact = queryDetail("PAY-CONFLICTING-FACT", "WAIT_CALLBACK");
        when(paymentMapper.findSubmissionFact("PAY-CONFLICTING-FACT")).thenReturn(submissionFact);
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-CONFLICTING-FACT");
        request.setPayScene("HOME_CLEAN");
        PaymentServiceImpl service = new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                paymentChannelRoutingService, paymentChannelQueryService, paymentChannelSubmitService);

        submissionFact.setPaymentSubmissionChannelValid(false);
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.prepay(request)).getCode());
        submissionFact.setPaymentSubmissionChannelValid(true);
        submissionFact.setLatestAttemptStatus("失败");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.prepay(request)).getCode());
        submissionFact.setLatestAttemptStatus("待回调");
        submissionFact.setLatestAttemptCompletedAt("2026-09-12 10:00:00");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.prepay(request)).getCode());
        verify(paymentMapper, never()).insertPaymentOrder(anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldDefaultPaymentStatusWhenReusingLegacyActivePrepay() {
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setSourceKind("STANDARD_ORDER");
        activePrepay.setPrepayOrderNo("PRE-LEGACY");
        activePrepay.setPaymentOrderId("PAY-LEGACY");
        activePrepay.setPayScene("HOME_CLEAN");
        activePrepay.setCustomerName("CUSTOMER");
        activePrepay.setAmount("¥168.00");
        activePrepay.setCashierTitle("CASHIER");
        when(paymentMapper.lockPrepayCreation("ORDER-001")).thenReturn("ORDER-001");
        when(paymentMapper.findOrderAmount("ORDER-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORDER-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORDER-001")).thenReturn("CUSTOMER");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORDER-001")).thenReturn(activePrepay);

        PrepayRequestDTO request = validPrepayRequest();
        request.setAmount(new BigDecimal("168.00"));

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .prepay(request);

        org.junit.jupiter.api.Assertions.assertEquals("PREPAY_CREATED", result.getPaymentStatus());
    }

    @Test
    void shouldRejectActiveStandardPrepayWhenOrderFactsHaveChanged() {
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setSourceKind("STANDARD_ORDER");
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
    void shouldRejectPrepayAmountWithTrailingScaleBeyondDatabasePrecisionBeforeOpeningLock() {
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-SCALE-001");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("99.000"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).prepay(request));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, exception.getCode());
        verify(paymentMapper, never()).initPrepayCreationLock(anyString());
        verify(paymentMapper, never()).lockPrepayCreation(anyString());
    }

    @Test
    void shouldRejectActivePrepayWhenRequestParametersConflict() {
        PrepayOrderDTO activePrepay = boundPrepay();
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
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldCreateNewPrepayWhenNoActivePrepayExists() {
        PrepayOrderDTO createdPrepay = boundPrepay();
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

        org.junit.jupiter.api.Assertions.assertEquals("CHAIN-PAY-NEW", result.getBusinessChainId());
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("STANDARD_ORDER"));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-NEW", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PREPAY_CREATED", result.getPaymentStatus());
        org.junit.jupiter.api.Assertions.assertEquals("PAY-NEW", result.getPaymentOrderId());
    }

    @Test
    void shouldStopVirtualPrepayWhenBillInsertDoesNotCreateExactlyOneRow() {
        when(paymentMapper.lockPrepayCreation("WALLET-BILL-FAIL")).thenReturn("WALLET-BILL-FAIL");
        when(paymentMapper.findLatestActivePrepayByOrderNo("WALLET-BILL-FAIL")).thenReturn(null);
        when(paymentMapper.findOrderAmount("WALLET-BILL-FAIL")).thenReturn(null);
        when(paymentMapper.findPaidAmount("WALLET-BILL-FAIL")).thenReturn(null);
        when(paymentMapper.insertBill(
                org.mockito.ArgumentMatchers.any(), eq("WALLET-BILL-FAIL"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), eq("待支付"), eq("warn")))
                .thenReturn(0);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-BILL-FAIL");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("20.00"));

        assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).prepay(request));
        verify(paymentMapper, never()).insertPaymentOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStopPrepayWhenPaymentOrderInsertDoesNotCreateExactlyOneRow() {
        when(paymentMapper.lockPrepayCreation("ORD-PAYMENT-FAIL")).thenReturn("ORD-PAYMENT-FAIL");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-PAYMENT-FAIL")).thenReturn(null);
        when(paymentMapper.findOrderAmount("ORD-PAYMENT-FAIL")).thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findPaidAmount("ORD-PAYMENT-FAIL")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-PAYMENT-FAIL")).thenReturn("张女士");
        when(paymentMapper.findBillNoByOrderNo("ORD-PAYMENT-FAIL")).thenReturn("BILL-PAYMENT-FAIL");
        when(paymentMapper.insertPaymentOrder(
                org.mockito.ArgumentMatchers.any(), eq("ORD-PAYMENT-FAIL"), eq("张女士"),
                org.mockito.ArgumentMatchers.any(), eq("HOME_CLEAN"))).thenReturn(0);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-PAYMENT-FAIL");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");

        assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).prepay(request));
        verify(paymentMapper, never()).insertPrepayOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStopPrepayWhenPrepayInsertDoesNotCreateExactlyOneRow() {
        when(paymentMapper.lockPrepayCreation("ORD-PREPAY-FAIL")).thenReturn("ORD-PREPAY-FAIL");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-PREPAY-FAIL")).thenReturn(null);
        when(paymentMapper.findOrderAmount("ORD-PREPAY-FAIL")).thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findPaidAmount("ORD-PREPAY-FAIL")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-PREPAY-FAIL")).thenReturn("张女士");
        when(paymentMapper.findBillNoByOrderNo("ORD-PREPAY-FAIL")).thenReturn("BILL-PREPAY-FAIL");
        when(paymentMapper.insertPrepayOrder(
                org.mockito.ArgumentMatchers.any(), eq("BILL-PREPAY-FAIL"), eq("ORD-PREPAY-FAIL"),
                eq("张女士"), org.mockito.ArgumentMatchers.any(), eq("HOME_CLEAN"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                eq("STANDARD_ORDER"))).thenReturn(0);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-PREPAY-FAIL");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");

        assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).prepay(request));
        verify(paymentMapper, never()).insertPaymentProcessLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectStandardPrepayWhenCreatedPrepayCannotBeReadBack() {
        when(paymentMapper.lockPrepayCreation("ORD-PREPAY-READBACK")).thenReturn("ORD-PREPAY-READBACK");
        when(paymentMapper.findLatestActivePrepayByOrderNo("ORD-PREPAY-READBACK")).thenReturn(null);
        when(paymentMapper.findOrderAmount("ORD-PREPAY-READBACK")).thenReturn(new BigDecimal("20.00"));
        when(paymentMapper.findPaidAmount("ORD-PREPAY-READBACK")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-PREPAY-READBACK")).thenReturn("张女士");
        when(paymentMapper.findBillNoByOrderNo("ORD-PREPAY-READBACK")).thenReturn("BILL-PREPAY-READBACK");
        when(paymentMapper.findPrepay(anyString())).thenReturn(null);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-PREPAY-READBACK");
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("张女士");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).prepay(request));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        verify(paymentMapper).insertPrepayOrder(
                anyString(), eq("BILL-PREPAY-READBACK"), eq("ORD-PREPAY-READBACK"),
                eq("张女士"), eq(new BigDecimal("20.00")), eq("HOME_CLEAN"),
                anyString(), anyString(), eq("STANDARD_ORDER"));
    }

    @Test
    void shouldRejectVirtualPrepayWhenCreatedPrepayCannotBeReadBack() {
        when(paymentMapper.lockPrepayCreation("WALLET-READBACK")).thenReturn("WALLET-READBACK");
        when(paymentMapper.findLatestActivePrepayByOrderNo("WALLET-READBACK")).thenReturn(null);
        when(paymentMapper.findOrderAmount("WALLET-READBACK")).thenReturn(null);
        when(paymentMapper.findPaidAmount("WALLET-READBACK")).thenReturn(null);
        when(paymentMapper.findPrepay(anyString())).thenReturn(null);

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("WALLET-READBACK");
        request.setPayScene("WALLET_RECHARGE_APP");
        request.setCustomerName("张女士");
        request.setAmount(new BigDecimal("20.00"));

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).prepay(request));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        verify(paymentMapper).insertPrepayOrder(
                anyString(), anyString(), eq("WALLET-READBACK"), eq("张女士"),
                eq(new BigDecimal("20.00")), eq("WALLET_RECHARGE_APP"), anyString(), anyString(), eq("VIRTUAL"));
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
                org.mockito.ArgumentMatchers.any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectInvalidStandardOrderAmountsBeforeCreatingPrepay() {
        when(paymentMapper.lockPrepayCreation("ORD-INVALID-SOURCE"))
                .thenReturn("ORD-INVALID-SOURCE");
        when(paymentMapper.findOrderAmount("ORD-INVALID-SOURCE"))
                .thenReturn(new BigDecimal("-10.00"), new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-INVALID-SOURCE"))
                .thenReturn(new BigDecimal("-20.00"), new BigDecimal("-1.00"));

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-INVALID-SOURCE");
        request.setPayScene("HOME_CLEAN");
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService);

        for (int attempt = 0; attempt < 2; attempt++) {
            BusinessException exception = assertThrows(BusinessException.class, () -> service.prepay(request));
            assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        }
        verify(paymentMapper, never()).findBillNoByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldRejectStandardPrepayWhenOrderCustomerIsMissing() {
        when(paymentMapper.lockPrepayCreation("ORD-NO-CUSTOMER"))
                .thenReturn("ORD-NO-CUSTOMER");
        when(paymentMapper.findOrderAmount("ORD-NO-CUSTOMER"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-NO-CUSTOMER"))
                .thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-NO-CUSTOMER"))
                .thenReturn(null, "   ");

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo("ORD-NO-CUSTOMER");
        request.setPayScene("HOME_CLEAN");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService);
        for (int attempt = 0; attempt < 2; attempt++) {
            BusinessException exception = assertThrows(BusinessException.class, () -> service.prepay(request));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        }
        verify(paymentMapper, never()).findBillNoByOrderNo(anyString());
        verify(paymentMapper, never()).insertPaymentOrder(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any(), anyString());
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
        PrepayOrderDTO createdPrepay = boundPrepay();
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
        request.setRemark("会员储值补充");

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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("VIRTUAL"));
        verify(paymentMapper).insertPaymentProcessLog(org.mockito.ArgumentMatchers.argThat(log ->
                "PREPAY_ORDER".equals(log.getSourceType())
                        && log.getSourceNo().startsWith("PRE")
                        && "预付单创建".equals(log.getProcessStage())
                        && "业务说明=会员储值补充".equals(log.getMessage())));
        org.junit.jupiter.api.Assertions.assertEquals("PRE-VIRTUAL", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("PREPAY_CREATED", result.getPaymentStatus());
    }

    @Test
    void shouldUsePaymentAmountToCloseBillWhenCallbackOrderSourceMissing() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("VIRTUAL");
        detail.setPaymentOrderId("PAY-VIRTUAL-CALLBACK");
        detail.setBillNo("BILL-VIRTUAL-CALLBACK");
        detail.setOrderNo("WALLET-RECHARGE-001");
        detail.setCustomerName("张女士");
        detail.setChannel("alipay_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥99.00");
        when(paymentMapper.findDetail("PAY-VIRTUAL-CALLBACK")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("WALLET-RECHARGE-001"))
                .thenReturn("WALLET-RECHARGE-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-VIRTUAL-CALLBACK", "ALI-VIRTUAL-001")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-VIRTUAL-CALLBACK")).thenReturn(1);
        when(paymentMapper.findOrderAmount("WALLET-RECHARGE-001")).thenReturn(null);
        when(paymentMapper.updateBillAfterPayment(
                "BILL-VIRTUAL-CALLBACK", new BigDecimal("99.00"), "已结清", "success",
                "WALLET-RECHARGE-001", "张女士", BigDecimal.ZERO)).thenReturn(1);
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString());
        verify(paymentMapper, times(1)).updateBillAfterPayment(
                "BILL-VIRTUAL-CALLBACK",
                new BigDecimal("99.00"),
                "已结清",
                "success",
                "WALLET-RECHARGE-001",
                "张女士",
                BigDecimal.ZERO);
    }

    @Test
    void shouldRejectStandardCallbackSettlementWhenSourceOrderDisappears() {
        PaymentDetailDTO detail = queryDetail("PAY-LOST-SOURCE", "WAIT_CALLBACK");
        detail.setSourceKind("STANDARD_ORDER");
        when(paymentMapper.findDetail("PAY-LOST-SOURCE")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult("PAY-LOST-SOURCE", "WX-LOST-SOURCE"))
                .thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-LOST-SOURCE")).thenReturn(1);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-LOST-SOURCE");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-LOST-SOURCE");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals("payment settlement standard order source is incomplete", exception.getMessage());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectVirtualCallbackSettlementWhenStandardOrderAppears() {
        PaymentDetailDTO detail = queryDetail("PAY-VIRTUAL-SOURCE-CHANGED", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-VIRTUAL-SOURCE-CHANGED")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-VIRTUAL-SOURCE-CHANGED", "WX-VIRTUAL-SOURCE-CHANGED")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-VIRTUAL-SOURCE-CHANGED"))
                .thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001")).thenReturn(new BigDecimal("168.00"));

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-VIRTUAL-SOURCE-CHANGED");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-VIRTUAL-SOURCE-CHANGED");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals("payment settlement virtual source has changed", exception.getMessage());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectStandardCallbackWhenOrderChangesAfterSourceRead() {
        PaymentDetailDTO detail = queryDetail("PAY-ORDER-CAS-LOST", "WAIT_CALLBACK");
        detail.setSourceKind("STANDARD_ORDER");
        detail.setCustomerName("张女士");
        when(paymentMapper.findDetail("PAY-ORDER-CAS-LOST")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult("PAY-ORDER-CAS-LOST", "WX-ORDER-CAS-LOST"))
                .thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-ORDER-CAS-LOST")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-QUERY-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-QUERY-001")).thenReturn("张女士");

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-ORDER-CAS-LOST");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-ORDER-CAS-LOST");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals("payment order settlement was not applied atomically", exception.getMessage());
        verify(paymentMapper).updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士");
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldPublishDownstreamEventWhenCallbackMarksPaymentSuccess() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("STANDARD_ORDER");
        detail.setCustomerName("张女士");
        detail.setPaymentOrderId("PAY-CALLBACK-001");
        detail.setBillNo("BILL-CALLBACK-001");
        detail.setOrderNo("ORD-CALLBACK-001");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setPayScene("HOME_CLEAN");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-001")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-CALLBACK-001"))
                .thenReturn("ORD-CALLBACK-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-001", "WX-CALLBACK-001")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-CALLBACK-001")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-CALLBACK-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-CALLBACK-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-CALLBACK-001")).thenReturn("张女士");
        when(paymentMapper.updateOrderAfterPayment(
                "ORD-CALLBACK-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士"))
                .thenReturn(1);
        when(paymentMapper.updateBillAfterPayment(
                "BILL-CALLBACK-001", new BigDecimal("168.00"), "已结清", "success",
                "ORD-CALLBACK-001", "张女士", BigDecimal.ZERO)).thenReturn(1);
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
                org.mockito.ArgumentMatchers.eq("{\"channel\":\"wx_h5\",\"payScene\":\"HOME_CLEAN\"}")
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
                "ORD-CALLBACK-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士");
    }

    @Test
    void shouldRejectSuccessfulCallbackWhenFinalPaymentDetailCannotBeReadBack() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("STANDARD_ORDER");
        detail.setCustomerName("张女士");
        detail.setPaymentOrderId("PAY-CALLBACK-READBACK");
        detail.setBillNo("BILL-CALLBACK-READBACK");
        detail.setOrderNo("ORD-CALLBACK-READBACK");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-READBACK"))
                .thenReturn(detail, detail, null);
        when(paymentMapper.lockPrepayCreation("ORD-CALLBACK-READBACK"))
                .thenReturn("ORD-CALLBACK-READBACK");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-READBACK", "WX-CALLBACK-READBACK")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-CALLBACK-READBACK"))
                .thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-CALLBACK-READBACK"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-CALLBACK-READBACK")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-CALLBACK-READBACK")).thenReturn("张女士");
        when(paymentMapper.updateOrderAfterPayment(
                "ORD-CALLBACK-READBACK", new BigDecimal("168.00"), "待履约", "info",
                BigDecimal.ZERO, "张女士"))
                .thenReturn(1);
        when(paymentMapper.updateBillAfterPayment(
                "BILL-CALLBACK-READBACK", new BigDecimal("168.00"), "已结清", "success",
                "ORD-CALLBACK-READBACK", "张女士", BigDecimal.ZERO))
                .thenReturn(1);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-READBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-READBACK");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("wx_h5", callback));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("支付单已更新但读取失败", exception.getMessage());
    }

    @Test
    void shouldRejectConcurrentSuccessCallbackWhenLatestPaymentDetailCannotBeReadBack() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setPaymentOrderId("PAY-CALLBACK-RACE-READBACK");
        detail.setOrderNo("ORD-CALLBACK-RACE-READBACK");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setAmount("¥168.00");
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        when(paymentMapper.findDetail("PAY-CALLBACK-RACE-READBACK"))
                .thenReturn(detail, detail, null);
        when(paymentMapper.lockPrepayCreation("ORD-CALLBACK-RACE-READBACK"))
                .thenReturn("ORD-CALLBACK-RACE-READBACK");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-RACE-READBACK", "WX-CALLBACK-RACE-READBACK"))
                .thenReturn(0);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-RACE-READBACK");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-RACE-READBACK");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("wx_h5", callback));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("支付单已更新但读取失败", exception.getMessage());
    }

    @Test
    void shouldFailSuccessSettlementWhenLatestAttemptCannotBeFinalized() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-NO-ATTEMPT", "WAIT_CALLBACK");
        detail.setOrderNo("ORD-CALLBACK-NO-ATTEMPT");
        when(paymentMapper.findDetail("PAY-CALLBACK-NO-ATTEMPT")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-CALLBACK-NO-ATTEMPT"))
                .thenReturn("ORD-CALLBACK-NO-ATTEMPT");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-NO-ATTEMPT", "WX-CALLBACK-NO-ATTEMPT")).thenReturn(1);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-NO-ATTEMPT");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-NO-ATTEMPT");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment attempt finalization was not applied atomically", exception.getMessage());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldFailSuccessSettlementWhenBoundBillCannotBeUpdated() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-BILL-MISSING", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-BILL-MISSING")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-CALLBACK-BILL-MISSING", "WX-CALLBACK-BILL-MISSING")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-CALLBACK-BILL-MISSING")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001")).thenReturn(null);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-BILL-MISSING");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-BILL-MISSING");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment bill settlement was not applied atomically", exception.getMessage());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotPublishDownstreamEventWhenCallbackStillPending() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setPaymentOrderId("PAY-CALLBACK-002");
        detail.setOrderNo("ORD-CALLBACK-002");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setStatus("WAIT_CALLBACK");
        detail.setLatestAttemptStatus("待回调");
        detail.setAmount("¥168.00");
        when(paymentMapper.findDetail("PAY-CALLBACK-002")).thenReturn(detail, detail);
        when(paymentMapper.markPaymentPendingFromCallback(
                "PAY-CALLBACK-002", "WX-CALLBACK-002")).thenReturn(1);
        when(paymentMapper.updateLatestPaymentAttemptStatus(
                "PAY-CALLBACK-002", "待回调", "warn")).thenReturn(1);
        when(paymentMapper.findRouteLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-CALLBACK-002")).thenReturn(Collections.emptyList());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId(" PAY-CALLBACK-002 ");
        callback.setTradeStatus(" PROCESSING ");
        callback.setChannelTransactionNo(" WX-CALLBACK-002 ");

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
                anyString(), eq("PAY-CALLBACK-002"), eq("wx_h5"), eq("PROCESSING"),
                eq("{\"tradeStatus\":\"PROCESSING\"}"), eq("{\"code\":\"SUCCESS\"}"),
                eq("已收口"), eq("success"));

        verify(paymentMapper).insertEvent(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("PAYMENT_PENDING"),
                org.mockito.ArgumentMatchers.eq("PAY-CALLBACK-002"),
                org.mockito.ArgumentMatchers.eq("ORD-CALLBACK-002"),
                org.mockito.ArgumentMatchers.eq("{\"channel\":\"wx_h5\"}")
        );
        org.junit.jupiter.api.Assertions.assertEquals("PAY-CALLBACK-002", callback.getPaymentOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("PROCESSING", callback.getTradeStatus());
        org.junit.jupiter.api.Assertions.assertEquals("WX-CALLBACK-002", callback.getChannelTransactionNo());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken("PAY-CALLBACK-002", "CALLBACK_SUCCESS");
    }

    @Test
    void shouldRejectUnknownPaymentCallbackTradeStatusWithoutSideEffects() {
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-UNKNOWN-STATUS");
        callback.setTradeStatus("PROCESS\"ING");
        callback.setChannelTransactionNo("WX-CALLBACK-UNKNOWN-STATUS");

        assertThrows(IllegalArgumentException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback));

        verify(paymentCallbackSignatureService, never()).verify(
                anyString(), org.mockito.ArgumentMatchers.any(PaymentCallbackRequestDTO.class));
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).markPaymentPendingFromCallback(anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldFailPendingCallbackWhenLatestAttemptCannotBeUpdated() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-PENDING-MISSING", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-PENDING-MISSING")).thenReturn(detail);
        when(paymentMapper.markPaymentPendingFromCallback(
                "PAY-CALLBACK-PENDING-MISSING", "WX-CALLBACK-PENDING-MISSING")).thenReturn(1);
        when(paymentMapper.updateLatestPaymentAttemptStatus(
                "PAY-CALLBACK-PENDING-MISSING", "待回调", "warn")).thenReturn(0);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-PENDING-MISSING");
        callback.setTradeStatus("PROCESSING");
        callback.setChannelTransactionNo("WX-CALLBACK-PENDING-MISSING");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment attempt pending update was not applied atomically", exception.getMessage());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotLetPendingCallbackOverwriteConcurrentSuccess() {
        PaymentDetailDTO pendingDetail = queryDetail("PAY-CALLBACK-PENDING-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-CALLBACK-PENDING-RACE", "SUCCESS");
        settledDetail.setChannelTransactionNo("WX-CALLBACK-PENDING-RACE");
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
        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldRejectPendingCallbackWhenConcurrentCallbackBoundDifferentTransaction() {
        PaymentDetailDTO initialDetail = queryDetail("PAY-CALLBACK-PENDING-BIND-RACE", "WAIT_CALLBACK");
        initialDetail.setChannelTransactionNo(null);
        PaymentDetailDTO concurrentlyBoundDetail =
                queryDetail("PAY-CALLBACK-PENDING-BIND-RACE", "WAIT_CALLBACK");
        concurrentlyBoundDetail.setChannelTransactionNo("WX-OTHER-CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-PENDING-BIND-RACE"))
                .thenReturn(initialDetail, concurrentlyBoundDetail);
        when(paymentMapper.markPaymentPendingFromCallback(
                "PAY-CALLBACK-PENDING-BIND-RACE", "WX-CURRENT-CALLBACK")).thenReturn(0);

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-PENDING-BIND-RACE");
        callback.setTradeStatus("PROCESSING");
        callback.setChannelTransactionNo("WX-CURRENT-CALLBACK");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldNotSettleWhenAnotherSuccessCallbackAlreadyWon() {
        PaymentDetailDTO pendingDetail = queryDetail("PAY-CALLBACK-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-CALLBACK-RACE", "SUCCESS");
        settledDetail.setChannelTransactionNo("WX-CALLBACK-RACE");
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
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        org.junit.jupiter.api.Assertions.assertEquals("SUCCESS", result.getStatus());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRejectCallbackWhenChannelMatchProjectionIsMissing() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-UNKNOWN-CHANNEL", "WAIT_CALLBACK");
        detail.setPaymentSubmissionChannelValid(null);
        when(paymentMapper.findDetail("PAY-CALLBACK-UNKNOWN-CHANNEL")).thenReturn(detail);
        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-UNKNOWN-CHANNEL");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-UNKNOWN-CHANNEL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", callback));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRejectSuccessCallbackWhenTransactionBindingChangesBeforeSettlementLock() {
        PaymentDetailDTO initialDetail = queryDetail("PAY-CALLBACK-TXN-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO lockedDetail = queryDetail("PAY-CALLBACK-TXN-RACE", "WAIT_CALLBACK");
        lockedDetail.setChannelTransactionNo("WX-OTHER-CALLBACK");
        when(paymentMapper.findDetail("PAY-CALLBACK-TXN-RACE"))
                .thenReturn(initialDetail, lockedDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY-CALLBACK-TXN-RACE");
        callback.setTradeStatus("SUCCESS");
        callback.setChannelTransactionNo("WX-CALLBACK-TXN-RACE");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .callback("wx_h5", callback));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
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
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReuseCurrentPrepayWhenSubmitAlreadyEnteredWaitCallback() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-100");
        prepay.setPaymentOrderId("PAY-100");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-100")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-100");
        detail.setAmount("¥168.00");
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        detail.setLatestAttemptStatus("待回调");
        detail.setPaymentSubmissionChannelValid(true);
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

        verify(paymentMapper, times(2)).findPrepay("PRE-100");
        verify(paymentMapper, times(1)).findDetail("PAY-100");
        verifyNoInteractions(paymentChannelRoutingService);
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-100");
        verify(paymentMapper, never()).insertRouteRecord(
                org.mockito.ArgumentMatchers.anyString(),
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-100", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", result.getChannel());
    }

    @Test
    void shouldRejectSubmittedReplayWhenPrepayBindingDriftsBeforeResponse() {
        PrepayOrderDTO initialPrepay = boundPrepay();
        initialPrepay.setPrepayOrderNo("PRE-REPLAY-DRIFT");
        initialPrepay.setPaymentOrderId("PAY-REPLAY-DRIFT");
        initialPrepay.setAmount("¥168.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo("PRE-REPLAY-DRIFT");
        latestPrepay.setPaymentOrderId("PAY-REPLAY-DRIFT");
        latestPrepay.setPrepayAmountBindingValid(false);
        when(paymentMapper.findPrepay("PRE-REPLAY-DRIFT"))
                .thenReturn(initialPrepay, latestPrepay);
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-REPLAY-DRIFT");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannel("wx_h5");
        detail.setAmount(initialPrepay.getAmount());
        detail.setLatestAttemptStatus("待回调");
        detail.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findDetail("PAY-REPLAY-DRIFT")).thenReturn(detail);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-REPLAY-DRIFT");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals("prepay and payment order amounts are inconsistent", exception.getMessage());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReuseSubmittedPaymentWithoutReroutingWhenDailyLimitLaterFills() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-LIMIT-REPLAY");
        prepay.setPaymentOrderId("PAY-LIMIT-REPLAY");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-LIMIT-REPLAY")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-LIMIT-REPLAY");
        detail.setAmount("¥80.00");
        detail.setStatus("WAIT_CALLBACK");
        detail.setChannel("wx_h5");
        detail.setLatestAttemptStatus("待回调");
        detail.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findDetail("PAY-LIMIT-REPLAY")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-LIMIT-REPLAY");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService).submit(request);

        assertEquals("PRE-LIMIT-REPLAY", result.getPrepayOrderNo());
        assertEquals("wx_h5", result.getChannel());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertPaymentAttempt(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

        PaymentSubmitRequestDTO differentChannel = new PaymentSubmitRequestDTO();
        differentChannel.setPrepayOrderNo("PRE-LIMIT-REPLAY");
        differentChannel.setPaymentMethod("微信支付");
        differentChannel.setChannelCode("DISABLED_CHANNEL");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(ErrorCode.PAYMENT_REQUESTED_CHANNEL_INVALID,
                        "requested channel is disabled"));
        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(differentChannel));
        assertEquals(ErrorCode.PAYMENT_REQUESTED_CHANNEL_INVALID, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReplayBoundPaymentWithoutCurrentOpsDefaults() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-OPS-REPLAY");
        prepay.setPaymentOrderId("PAY-OPS-REPLAY");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-OPS-REPLAY")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-OPS-REPLAY");
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        detail.setLatestAttemptStatus("待回调");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-OPS-REPLAY")).thenReturn(detail);

        String idempotencyKey = "PRE-OPS-REPLAY|微信支付|wx_h5";
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)).thenReturn(true);
        PaymentAttemptIdempotencyDTO existingAttempt = new PaymentAttemptIdempotencyDTO();
        existingAttempt.setPrepayOrderNo("PRE-OPS-REPLAY");
        existingAttempt.setPaymentOrderId("PAY-OPS-REPLAY");
        when(paymentMapper.findPaymentAttemptIdempotency(idempotencyKey)).thenReturn(existingAttempt);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-OPS-REPLAY");
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService, paymentEventDispatchService,
                paymentRiskControlService, paymentOpsConfigService);
        PaymentSubmitRequestDTO effectiveRequest = new PaymentSubmitRequestDTO();
        effectiveRequest.setPrepayOrderNo("PRE-OPS-REPLAY");
        effectiveRequest.setPaymentMethod("微信支付");
        existingAttempt.setRequestFingerprint(service.submitRequestFingerprint(
                effectiveRequest, prepay, "PAY-OPS-REPLAY", "default-app", "wx_h5", "UNKNOWN"));
        PrepayOrderDTO result = service.submit(request);

        assertEquals("wx_h5", result.getChannel());
        assertEquals("微信支付", result.getPaymentMethod());
        verifyNoInteractions(paymentOpsConfigService, paymentChannelRoutingService, paymentChannelSubmitService);
        verify(paymentMapper).findPaymentAttemptIdempotency(idempotencyKey);
    }

    @Test
    void shouldRejectBoundPaymentReplayWithDifferentPaymentMethod() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-METHOD-REPLAY");
        prepay.setPaymentOrderId("PAY-METHOD-REPLAY");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-METHOD-REPLAY")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-METHOD-REPLAY");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        when(paymentMapper.findDetail("PAY-METHOD-REPLAY")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-METHOD-REPLAY");
        request.setPaymentMethod("支付宝");
        request.setChannelCode("WX_H5");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
        verifyNoInteractions(paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectBoundPaymentReplayWithAnotherAvailableChannel() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CHANNEL-REPLAY");
        prepay.setPaymentOrderId("PAY-CHANNEL-REPLAY");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-CHANNEL-REPLAY")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-CHANNEL-REPLAY");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        when(paymentMapper.findDetail("PAY-CHANNEL-REPLAY")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_jsapi");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-CHANNEL-REPLAY");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_jsapi");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
        verify(paymentChannelRoutingService).resolve(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(paymentChannelSubmitService);
    }

    @Test
    void shouldRejectPendingPaymentReplayWithoutBoundChannel() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-MISSING-CHANNEL");
        prepay.setPaymentOrderId("PAY-MISSING-CHANNEL");
        prepay.setAmount("¥80.00");
        prepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay("PRE-MISSING-CHANNEL")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-MISSING-CHANNEL");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setLatestAttemptStatus("待回调");
        when(paymentMapper.findDetail("PAY-MISSING-CHANNEL")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-MISSING-CHANNEL");
        request.setPaymentMethod("微信支付");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelSubmitService);
    }

    @Test
    void shouldRejectPendingPaymentReplayWithoutMatchingSubmitAttempt() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-MISSING-ATTEMPT");
        prepay.setPaymentOrderId("PAY-MISSING-ATTEMPT");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-MISSING-ATTEMPT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-MISSING-ATTEMPT");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail("PAY-MISSING-ATTEMPT")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-MISSING-ATTEMPT");
        request.setPaymentMethod("微信支付");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectPendingPaymentReplayWithTerminalSubmitAttempt() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-TERMINAL-ATTEMPT");
        prepay.setPaymentOrderId("PAY-TERMINAL-ATTEMPT");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-TERMINAL-ATTEMPT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-TERMINAL-ATTEMPT");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findDetail("PAY-TERMINAL-ATTEMPT")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-TERMINAL-ATTEMPT");
        request.setPaymentMethod("微信支付");
        for (String terminalStatus : Arrays.asList("成功", "支付成功", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED")) {
            detail.setLatestAttemptStatus(terminalStatus);
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    new PaymentServiceImpl(
                            paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                            paymentChannelQueryService, paymentChannelSubmitService).submit(request));
            assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        }
        verifyNoInteractions(paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectPendingPaymentReplayWithCompletedSubmitAttempt() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-COMPLETED-ATTEMPT");
        prepay.setPaymentOrderId("PAY-COMPLETED-ATTEMPT");
        prepay.setAmount("¥80.00");
        when(paymentMapper.findPrepay("PRE-COMPLETED-ATTEMPT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-COMPLETED-ATTEMPT");
        detail.setAmount(prepay.getAmount());
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setLatestAttemptStatus("处理中");
        detail.setLatestAttemptCompletedAt("2026-10-06 10:00:00");
        when(paymentMapper.findDetail("PAY-COMPLETED-ATTEMPT")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-COMPLETED-ATTEMPT");
        request.setPaymentMethod("微信支付");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldReturnLatestSuccessAttemptFactsWhenSubmitReusesSuccessfulPayment() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-SUCCESS-100");
        prepay.setPaymentOrderId("PAY-SUCCESS-100");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-SUCCESS-100")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("VIRTUAL");
        detail.setPaymentOrderId("PAY-SUCCESS-100");
        detail.setOrderNo(prepay.getOrderNo());
        detail.setAmount("¥168.00");
        detail.setStatus("SUCCESS");
        detail.setStatusType("success");
        detail.setChannel("wx_h5");
        detail.setChannelTransactionNo("WX-SUCCESS-100");
        detail.setLatestAttemptStatus("成功");
        detail.setLatestAttemptStatusType("success");
        detail.setLatestAttemptCompletedAt("2026-09-27 12:00:00");
        detail.setSuccessAt("2026-09-27 12:01:00");
        detail.setCreatedAt("2026-09-27 11:59:00");
        detail.setPaymentFactTimelineValid(true);
        when(paymentMapper.findDetail("PAY-SUCCESS-100")).thenReturn(detail);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-SUCCESS-100");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");

        PrepayOrderDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .submit(request);

        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", result.getChannel());
        org.junit.jupiter.api.Assertions.assertEquals("WX-SUCCESS-100", result.getChannelTransactionNo());
        org.junit.jupiter.api.Assertions.assertEquals("成功", result.getLatestAttemptStatus());
        org.junit.jupiter.api.Assertions.assertEquals("success", result.getLatestAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertEquals("2026-09-27 12:00:00", result.getLatestAttemptCompletedAt());
        org.junit.jupiter.api.Assertions.assertEquals("2026-09-27 12:01:00", result.getSuccessAt());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRepeatedSubmitWhenSuccessfulPaymentTimelineIsInverted() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-SUCCESS-INVALID");
        prepay.setPaymentOrderId("PAY-SUCCESS-INVALID");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-SUCCESS-INVALID")).thenReturn(prepay);
        PaymentDetailDTO detail = queryDetail("PAY-SUCCESS-INVALID", "SUCCESS");
        detail.setPaymentFactTimelineValid(false);
        when(paymentMapper.findDetail("PAY-SUCCESS-INVALID")).thenReturn(detail);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-SUCCESS-INVALID");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRepeatedSubmitWhenPrepayClaimsSuccessButPaymentIsPending() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-SUCCESS-MISMATCH");
        prepay.setPaymentOrderId("PAY-SUCCESS-MISMATCH");
        prepay.setPaymentStatus("SUCCESS");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-SUCCESS-MISMATCH")).thenReturn(prepay);
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-SUCCESS-MISMATCH");
        detail.setStatus("WAIT_CALLBACK");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-SUCCESS-MISMATCH")).thenReturn(detail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-SUCCESS-MISMATCH");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRepeatedSubmitWhenAccessTokenIsInvalid() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-101");
        prepay.setPaymentOrderId("PAY-101");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-101")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-101");
        detail.setAmount("¥168.00");
        detail.setStatus("WAIT_CALLBACK");
        detail.setPaymentMethod("微信支付");
        detail.setChannel("wx_h5");
        when(paymentMapper.findDetail("PAY-101")).thenReturn(detail);

        PaymentControlPolicyDTO controlPolicy = new PaymentControlPolicyDTO();
        controlPolicy.setSourceAppId("housekeeping-app-web");
        controlPolicy.setAllowedPaymentMethods("微信支付");
        controlPolicy.setAllowedChannelCodes("wx_h5");
        controlPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        controlPolicy.setTokenAuthRequired("开启");
        controlPolicy.setAccessTokenValue("token-housekeeping-app-web");
        controlPolicy.setStrictMode("关闭");
        controlPolicy.setSelfCheckStatus("PASS");
        when(paymentMapper.findActiveControlPolicyBySourceAppId("housekeeping-app-web"))
                .thenReturn(controlPolicy);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-101");
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
                        .submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, exception.getCode());
        verify(paymentChannelRoutingService, never()).resolve(org.mockito.ArgumentMatchers.any());
        verify(paymentChannelSubmitService, never()).submit(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectSubmitWhenUnsubmittedPrepayHasPassedExpiry() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setAmount("¥168.00");
        prepay.setPrepayOrderNo("PRE-EXPIRED-SUBMIT");
        prepay.setPaymentOrderId("PAY-EXPIRED-SUBMIT");
        when(paymentMapper.findPrepay("PRE-EXPIRED-SUBMIT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-EXPIRED-SUBMIT");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-EXPIRED-SUBMIT")).thenReturn(detail);
        when(paymentMapper.isPrepayExpired("PRE-EXPIRED-SUBMIT")).thenReturn(true);

        PaymentSubmitRequestDTO request = validSubmitRequest();
        request.setPrepayOrderNo("PRE-EXPIRED-SUBMIT");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_PREPAY_EXPIRED, exception.getCode());
        verify(paymentMapper).isPrepayExpired("PRE-EXPIRED-SUBMIT");
        verifyNoInteractions(paymentChannelRoutingService, paymentRiskControlService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectSubmitBeforeRoutingWhenStandardOrderOutstandingChanged() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setSourceKind("STANDARD_ORDER");
        prepay.setOrderNo("ORD-SOURCE-DRIFT");
        prepay.setPrepayOrderNo("PRE-SOURCE-DRIFT");
        prepay.setPaymentOrderId("PAY-SOURCE-DRIFT");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-SOURCE-DRIFT")).thenReturn(prepay);
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-SOURCE-DRIFT");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-SOURCE-DRIFT")).thenReturn(detail);
        when(paymentMapper.findOrderAmount("ORD-SOURCE-DRIFT")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-SOURCE-DRIFT")).thenReturn(new BigDecimal("50.00"));
        when(paymentMapper.findCustomerNameByOrderNo("ORD-SOURCE-DRIFT")).thenReturn("张女士");

        PaymentSubmitRequestDTO request = validSubmitRequest();
        request.setPrepayOrderNo("PRE-SOURCE-DRIFT");
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT, exception.getCode());
        verifyNoInteractions(paymentChannelRoutingService, paymentRiskControlService, paymentChannelSubmitService);
        verify(paymentMapper, never()).updatePrepayToPaying(anyString());
    }

    @Test
    void shouldSkipDuplicateSubmitWhenIdempotencyKeyAlreadyExists() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-200");
        prepay.setPaymentOrderId("PAY-200");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-200")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-200");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-200")).thenReturn(detail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-200")).thenReturn(true);
        PaymentAttemptIdempotencyDTO existingAttempt = new PaymentAttemptIdempotencyDTO();
        existingAttempt.setPrepayOrderNo("PRE-200");
        existingAttempt.setPaymentOrderId("PAY-200");
        when(paymentMapper.findPaymentAttemptIdempotency("IDEMP-200")).thenReturn(existingAttempt);
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
        existingAttempt.setRequestFingerprint(service.submitRequestFingerprint(
                request, prepay, "PAY-200", "default-app", "wx_h5", "UNKNOWN"));
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.junit.jupiter.api.Assertions.assertEquals("PRE-200", result.getPrepayOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals("微信支付", result.getPaymentMethod());
    }

    @Test
    void shouldRejectIdempotencyKeyOwnedByAnotherPayment() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-201");
        prepay.setPaymentOrderId("PAY-201");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay("PRE-201")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-201");
        detail.setAmount("¥168.00");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-201")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-201")).thenReturn(true);
        PaymentAttemptIdempotencyDTO existingAttempt = new PaymentAttemptIdempotencyDTO();
        existingAttempt.setPrepayOrderNo("PRE-OTHER");
        existingAttempt.setPaymentOrderId("PAY-OTHER");
        when(paymentMapper.findPaymentAttemptIdempotency("IDEMP-201")).thenReturn(existingAttempt);

        PaymentSubmitRequestDTO request = validSubmitRequest();
        request.setPrepayOrderNo("PRE-201");
        request.setIdempotencyKey("IDEMP-201");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(
                org.mockito.ArgumentMatchers.any(PaymentChannelSubmitRequestDTO.class));
    }

    @Test
    void shouldRejectMissingSubmitAdapterBeforeWritingChannelSubmissionFacts() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-NO-ADAPTER");
        prepay.setPaymentOrderId("PAY-NO-ADAPTER");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("测试客户");
        when(paymentMapper.findPrepay("PRE-NO-ADAPTER")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-NO-ADAPTER");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentChannelSubmitService, never()).submit(
                org.mockito.ArgumentMatchers.any(PaymentChannelSubmitRequestDTO.class));
    }

    @Test
    void shouldRejectOversizedSubmitIdempotencyKeyBeforeControlAndChannelCalls() {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-IDEMP-LONG");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey(repeat("A", 128) + " ");

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
    void shouldKeepGeneratedSubmitIdempotencyKeyWithinColumnLimit() throws Exception {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "buildIdempotencyKey", PaymentSubmitRequestDTO.class, PrepayOrderDTO.class, String.class);
        method.setAccessible(true);
        PrepayOrderDTO prepay = new PrepayOrderDTO();
        prepay.setPrepayOrderNo("PRE-" + repeat("P", 60));
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPaymentMethod(repeat("M", 32));
        String channel = repeat("C", 64);

        String generated = (String) method.invoke(service, request, prepay, channel);
        assertTrue(generated.length() <= 128);
        assertTrue(generated.matches("AUTO:[0-9a-f]{64}"));
        assertFalse(generated.contains(prepay.getPrepayOrderNo()));
        assertEquals(generated, method.invoke(service, request, prepay, channel));
        assertNotEquals(generated, method.invoke(service, request, prepay, "D" + repeat("C", 63)));
        request.setPaymentMethod(repeat("M", 30) + "|X");
        String delimited = (String) method.invoke(service, request, prepay, repeat("C", 60));
        request.setPaymentMethod(repeat("M", 30));
        assertNotEquals(delimited, method.invoke(service, request, prepay, "X|" + repeat("C", 60)));
        request.setIdempotencyKey("EXPLICIT-KEY");
        assertEquals("EXPLICIT-KEY", method.invoke(service, request, prepay, channel));
        request.setIdempotencyKey(null);
        prepay.setPrepayOrderNo("PRE-SHORT");
        request.setPaymentMethod("微信支付");
        assertEquals("PRE-SHORT|微信支付|wx_h5", method.invoke(service, request, prepay, "wx_h5"));
    }

    @Test
    void shouldUseBoundedGeneratedKeyInSubmitAttemptLookup() {
        String prepayOrderNo = "PRE-" + repeat("P", 60);
        String paymentMethod = repeat("M", 32);
        String channelCode = repeat("C", 64);
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo(prepayOrderNo);
        prepay.setPaymentOrderId("PAY-AUTO-LONG");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("测试客户");
        when(paymentMapper.findPrepay(prepayOrderNo)).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-AUTO-LONG");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-AUTO-LONG")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode(channelCode);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING, "no payment channel submit adapter available"))
                .when(paymentChannelSubmitService).ensureAdapterAvailable(channelCode);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepayOrderNo);
        request.setPaymentMethod(paymentMethod);
        request.setChannelCode(channelCode);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING, exception.getCode());
        verify(paymentMapper, times(2)).existsPaymentAttemptByIdempotencyKey(
                argThat(key -> key.matches("AUTO:[0-9a-f]{64}")));
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
    void shouldRejectUnpairedSurrogatesInSubmitIdempotencyKeyBeforeMapperAccess() {
        for (char surrogate : new char[] { (char) 0xD800, (char) 0xDC00 }) {
            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo("PRE-IDEMP-UNICODE");
            request.setPaymentMethod("微信支付");
            request.setIdempotencyKey("IDEMP-" + surrogate);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> new PaymentServiceImpl(
                            paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                            paymentChannelQueryService, paymentChannelSubmitService).submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID, exception.getCode());
        }
        verifyNoInteractions(paymentMapper, paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldRejectUnpairedSurrogatesInSubmitFieldsBeforeMapperAccess() {
        List<BiConsumer<PaymentSubmitRequestDTO, String>> fields = Arrays.asList(
                PaymentSubmitRequestDTO::setPrepayOrderNo,
                PaymentSubmitRequestDTO::setPaymentMethod,
                PaymentSubmitRequestDTO::setChannelCode,
                PaymentSubmitRequestDTO::setSourceAppId,
                PaymentSubmitRequestDTO::setMerchantNo,
                PaymentSubmitRequestDTO::setAccessToken,
                PaymentSubmitRequestDTO::setTerminal,
                PaymentSubmitRequestDTO::setClientIp,
                PaymentSubmitRequestDTO::setClientDeviceId,
                PaymentSubmitRequestDTO::setPayerPhone);
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService);
        for (char surrogate : new char[] { (char) 0xD800, (char) 0xDC00 }) {
            for (BiConsumer<PaymentSubmitRequestDTO, String> field : fields) {
                PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
                request.setPrepayOrderNo("PRE-INVALID-UNICODE");
                field.accept(request, "INVALID-" + surrogate);
                BusinessException exception = assertThrows(BusinessException.class, () -> service.submit(request));
                assertEquals(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
            }
        }
        verifyNoInteractions(paymentMapper, paymentChannelRoutingService, paymentChannelSubmitService);
    }

    @Test
    void shouldReturnLatestPrepayWhenConcurrentSubmitAlreadyOccupiedCashier() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-250");
        prepay.setPaymentOrderId("PAY-250");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("李女士");
        when(paymentMapper.findPrepay("PRE-250")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-250");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-250")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政 H5 微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("IDEMP-250")).thenReturn(false);
        when(paymentMapper.findSubmitConcurrencyToken("PRE-250", "default-app")).thenReturn(null);
        when(paymentMapper.updatePrepayToPaying("PRE-250")).thenReturn(0);

        PrepayOrderDTO latestPrepay = boundPrepay();
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

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        PaymentAttemptIdempotencyDTO winningAttempt = new PaymentAttemptIdempotencyDTO();
        winningAttempt.setPrepayOrderNo(prepay.getPrepayOrderNo());
        winningAttempt.setPaymentOrderId(prepay.getPaymentOrderId());
        winningAttempt.setRequestFingerprint(service.submitRequestFingerprint(
                request, prepay, prepay.getPaymentOrderId(), "default-app", "wx_h5", "UNKNOWN"));
        when(paymentMapper.findPaymentAttemptIdempotency(request.getIdempotencyKey()))
                .thenReturn(winningAttempt);
        PrepayOrderDTO result = service.submit(request);

        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
        org.junit.jupiter.api.Assertions.assertEquals("支付中", result.getCashierStatus());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectChangedContentAfterConcurrentCashierOccupancy() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-CONTENT");
        prepay.setPaymentOrderId("PAY-CONCURRENT-CONTENT");
        prepay.setAmount("¥88.00");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay);
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId(prepay.getPaymentOrderId());
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail(prepay.getPaymentOrderId())).thenReturn(detail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.updatePrepayToPaying(prepay.getPrepayOrderNo())).thenReturn(0);
        PaymentAttemptIdempotencyDTO winningAttempt = new PaymentAttemptIdempotencyDTO();
        winningAttempt.setPrepayOrderNo(prepay.getPrepayOrderNo());
        winningAttempt.setPaymentOrderId(prepay.getPaymentOrderId());
        winningAttempt.setRequestFingerprint("different-content");
        when(paymentMapper.findPaymentAttemptIdempotency("IDEMP-CONCURRENT-CONTENT"))
                .thenReturn(winningAttempt);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        request.setIdempotencyKey("IDEMP-CONCURRENT-CONTENT");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, exception.getCode());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectConcurrentPendingWinnerWithoutSubmitAttempt() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-NO-ATTEMPT");
        prepay.setPaymentOrderId("PAY-CONCURRENT-NO-ATTEMPT");
        prepay.setAmount("¥80.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        latestPrepay.setPaymentOrderId(prepay.getPaymentOrderId());
        latestPrepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay, latestPrepay);

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        initialDetail.setAmount(prepay.getAmount());
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO pendingDetail = new PaymentDetailDTO();
        pendingDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        pendingDetail.setStatus("WAIT_CALLBACK");
        pendingDetail.setChannel("wx_h5");
        pendingDetail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail(prepay.getPaymentOrderId())).thenReturn(initialDetail, pendingDetail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentMapper.updatePrepayToPaying(prepay.getPrepayOrderNo())).thenReturn(0);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectConcurrentIdempotencyWinnerFromAnotherChannel() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-OTHER-CHANNEL");
        prepay.setPaymentOrderId("PAY-CONCURRENT-OTHER-CHANNEL");
        prepay.setAmount("¥80.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        latestPrepay.setPaymentOrderId(prepay.getPaymentOrderId());
        latestPrepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay, latestPrepay);

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        initialDetail.setAmount(prepay.getAmount());
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO pendingDetail = new PaymentDetailDTO();
        pendingDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        pendingDetail.setStatus("WAIT_CALLBACK");
        pendingDetail.setChannel("wx_jsapi");
        pendingDetail.setPaymentMethod("微信支付");
        pendingDetail.setLatestAttemptStatus("待回调");
        pendingDetail.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findDetail(prepay.getPaymentOrderId())).thenReturn(initialDetail, pendingDetail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        String idempotencyKey = "PRE-CONCURRENT-OTHER-CHANNEL|微信支付|wx_h5";
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey))
                .thenReturn(false, false, true);
        PaymentAttemptIdempotencyDTO existingAttempt = new PaymentAttemptIdempotencyDTO();
        existingAttempt.setPrepayOrderNo(prepay.getPrepayOrderNo());
        existingAttempt.setPaymentOrderId(prepay.getPaymentOrderId());
        when(paymentMapper.findPaymentAttemptIdempotency(idempotencyKey)).thenReturn(existingAttempt);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        existingAttempt.setRequestFingerprint(new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).submitRequestFingerprint(
                request, prepay, prepay.getPaymentOrderId(), "default-app", "wx_h5", "UNKNOWN"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectConcurrentPendingWinnerWithAliasedRouteChannel() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-CHANNEL-ALIAS");
        prepay.setPaymentOrderId("PAY-CONCURRENT-CHANNEL-ALIAS");
        prepay.setAmount("¥80.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        latestPrepay.setPaymentOrderId(prepay.getPaymentOrderId());
        latestPrepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay, latestPrepay);

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        initialDetail.setAmount(prepay.getAmount());
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO pendingDetail = new PaymentDetailDTO();
        pendingDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        pendingDetail.setStatus("WAIT_CALLBACK");
        pendingDetail.setChannel("WX_H5");
        pendingDetail.setPaymentMethod("微信支付");
        pendingDetail.setLatestAttemptStatus("待回调");
        pendingDetail.setPaymentSubmissionChannelValid(true);
        when(paymentMapper.findDetail(prepay.getPaymentOrderId())).thenReturn(initialDetail, pendingDetail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentMapper.updatePrepayToPaying(prepay.getPrepayOrderNo())).thenReturn(0);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_SUBMIT_REQUEST_INVALID, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectAlreadyRecordedIdempotencyWinnerWithoutSubmissionFact() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-RECORDED-NO-FACT");
        prepay.setPaymentOrderId("PAY-RECORDED-NO-FACT");
        prepay.setAmount("¥80.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        latestPrepay.setPaymentOrderId(prepay.getPaymentOrderId());
        latestPrepay.setPaymentStatus("WAIT_CALLBACK");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay, latestPrepay);

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        initialDetail.setAmount(prepay.getAmount());
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO pendingDetail = new PaymentDetailDTO();
        pendingDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        pendingDetail.setStatus("WAIT_CALLBACK");
        pendingDetail.setChannel("wx_h5");
        pendingDetail.setLatestAttemptStatus("待回调");
        pendingDetail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail(prepay.getPaymentOrderId())).thenReturn(initialDetail, pendingDetail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        String idempotencyKey = "PRE-RECORDED-NO-FACT|微信支付|wx_h5";
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey(idempotencyKey)).thenReturn(true);
        PaymentAttemptIdempotencyDTO attempt = new PaymentAttemptIdempotencyDTO();
        attempt.setPrepayOrderNo(prepay.getPrepayOrderNo());
        attempt.setPaymentOrderId(prepay.getPaymentOrderId());
        when(paymentMapper.findPaymentAttemptIdempotency(idempotencyKey)).thenReturn(attempt);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        attempt.setRequestFingerprint(new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).submitRequestFingerprint(
                request, prepay, prepay.getPaymentOrderId(), "default-app", "wx_h5", "UNKNOWN"));

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectConcurrentSubmitWhenLatestPrepayBindingDrifts() {
        PrepayOrderDTO initialPrepay = boundPrepay();
        initialPrepay.setPrepayOrderNo("PRE-CONCURRENT-BINDING");
        initialPrepay.setPaymentOrderId("PAY-CONCURRENT-BINDING");
        initialPrepay.setAmount("¥168.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo("PRE-CONCURRENT-BINDING");
        latestPrepay.setPaymentOrderId("PAY-CONCURRENT-BINDING");
        latestPrepay.setPrepayAmountBindingValid(false);
        when(paymentMapper.findPrepay("PRE-CONCURRENT-BINDING"))
                .thenReturn(initialPrepay, latestPrepay);
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-CONCURRENT-BINDING");
        initialDetail.setStatus("PREPAY_CREATED");
        initialDetail.setAmount(initialPrepay.getAmount());
        PaymentDetailDTO latestDetail = new PaymentDetailDTO();
        latestDetail.setPaymentOrderId("PAY-CONCURRENT-BINDING");
        latestDetail.setStatus("WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-CONCURRENT-BINDING"))
                .thenReturn(initialDetail, latestDetail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-CONCURRENT-BINDING")).thenReturn(0);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-CONCURRENT-BINDING");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setIdempotencyKey("IDEMP-CONCURRENT-BINDING");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals("PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException", exception.getMessage());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectConcurrentSubmitWhenWinnerHasInvalidSuccessFacts() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-SUCCESS");
        prepay.setPaymentOrderId("PAY-CONCURRENT-SUCCESS");
        prepay.setAmount("¥168.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo("PRE-CONCURRENT-SUCCESS");
        latestPrepay.setPaymentOrderId("PAY-CONCURRENT-SUCCESS");
        latestPrepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CONCURRENT-SUCCESS"))
                .thenReturn(prepay, latestPrepay);
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-CONCURRENT-SUCCESS");
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO invalidDetail = queryDetail("PAY-CONCURRENT-SUCCESS", "SUCCESS");
        invalidDetail.setPaymentFactTimelineValid(false);
        initialDetail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-CONCURRENT-SUCCESS"))
                .thenReturn(initialDetail, invalidDetail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-CONCURRENT-SUCCESS");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setIdempotencyKey("IDEMP-CONCURRENT-SUCCESS");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request));

        assertEquals("PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnValidatedSuccessFactsWhenConcurrentSubmitWins() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-CONCURRENT-VALID");
        prepay.setPaymentOrderId("PAY-CONCURRENT-VALID");
        prepay.setAmount("¥168.00");
        PrepayOrderDTO latestPrepay = boundPrepay();
        latestPrepay.setPrepayOrderNo("PRE-CONCURRENT-VALID");
        latestPrepay.setPaymentOrderId("PAY-CONCURRENT-VALID");
        latestPrepay.setPaymentStatus("SUCCESS");
        when(paymentMapper.findPrepay("PRE-CONCURRENT-VALID"))
                .thenReturn(prepay, latestPrepay);
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-CONCURRENT-VALID");
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO successDetail = queryDetail("PAY-CONCURRENT-VALID", "SUCCESS");
        initialDetail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-CONCURRENT-VALID"))
                .thenReturn(initialDetail, successDetail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-CONCURRENT-VALID");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("WX_H5");
        request.setIdempotencyKey("IDEMP-CONCURRENT-VALID");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        PaymentAttemptIdempotencyDTO winningAttempt = new PaymentAttemptIdempotencyDTO();
        winningAttempt.setPrepayOrderNo(prepay.getPrepayOrderNo());
        winningAttempt.setPaymentOrderId(prepay.getPaymentOrderId());
        winningAttempt.setRequestFingerprint(service.submitRequestFingerprint(
                request, prepay, prepay.getPaymentOrderId(), "default-app", "wx_h5", "UNKNOWN"));
        when(paymentMapper.findPaymentAttemptIdempotency(request.getIdempotencyKey()))
                .thenReturn(winningAttempt);
        PrepayOrderDTO result = service.submit(request);

        assertEquals("SUCCESS", result.getPaymentStatus());
        assertEquals("成功", result.getLatestAttemptStatus());
        assertEquals("2026-09-12 10:00:00", result.getLatestAttemptCompletedAt());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectWhenPrepayStatusUpdateAffectsMultipleRows() throws Exception {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-MULTI-250");
        prepay.setPaymentOrderId("PAY-MULTI-250");
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-MULTI-250");
        request.setPaymentMethod("微信支付");
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentMapper.findSubmitConcurrencyToken("PRE-MULTI-250", "default-app"))
                .thenReturn(null);
        when(paymentMapper.insertSubmitConcurrencyToken(
                "PRE-MULTI-250", "PAY-MULTI-250", "default-app", "IDEMP-MULTI-250",
                "H5", "127.0.0.1", 120)).thenReturn(1);
        when(paymentMapper.updatePrepayToPaying("PRE-MULTI-250")).thenReturn(2);

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "prepareSubmit",
                PaymentSubmitRequestDTO.class, PrepayOrderDTO.class, String.class, String.class,
                String.class, String.class, String.class, PaymentRouteDecisionDTO.class,
                String.class, String.class, String.class, String.class, String.class, Long.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, request, prepay, "PAY-MULTI-250", "default-app",
                        "H5", "127.0.0.1", "IDEMP-MULTI-250", routeDecision, "ORD-MULTI-250",
                        "ROUTE-MULTI-250", "ATT-MULTI-250", "{}", "fingerprint", null));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment prepay status update affected multiple rows",
                exception.getCause().getMessage());
        verify(paymentMapper, never()).insertPaymentAttempt(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectNewActivePaymentDuringSubmitPreparationBeforeWritingAttempt() throws Exception {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setOrderNo("ORD-DUPLICATE-SUBMIT");
        prepay.setPrepayOrderNo("PRE-DUPLICATE-SUBMIT");
        prepay.setPaymentOrderId("PAY-DUPLICATE-SUBMIT");
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        activePrepay.setActivePrepayCandidateCount(2);
        when(paymentMapper.findLatestActivePrepayByOrderNo(prepay.getOrderNo()))
                .thenReturn(activePrepay);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "prepareSubmit",
                PaymentSubmitRequestDTO.class, PrepayOrderDTO.class, String.class, String.class,
                String.class, String.class, String.class, PaymentRouteDecisionDTO.class,
                String.class, String.class, String.class, String.class, String.class, Long.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, request, prepay, prepay.getPaymentOrderId(), "default-app",
                        "H5", "127.0.0.1", "IDEMP-DUPLICATE", new PaymentRouteDecisionDTO(),
                        prepay.getOrderNo(), "ROUTE-DUPLICATE", "ATT-DUPLICATE", "{}", "fingerprint", null));

        assertEquals("active prepay orders for the same order are ambiguous",
                exception.getCause().getMessage());
        verify(paymentMapper).lockPrepayCreation(prepay.getOrderNo());
        verify(paymentMapper, never()).updatePrepayToPaying(anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectExpiryAfterSubmitEntryBeforeWritingAttempt() throws Exception {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setOrderNo("ORD-EXPIRED-PREPARATION");
        prepay.setPrepayOrderNo("PRE-EXPIRED-PREPARATION");
        prepay.setPaymentOrderId("PAY-EXPIRED-PREPARATION");
        when(paymentMapper.isPrepayExpired(prepay.getPrepayOrderNo())).thenReturn(true);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "prepareSubmit",
                PaymentSubmitRequestDTO.class, PrepayOrderDTO.class, String.class, String.class,
                String.class, String.class, String.class, PaymentRouteDecisionDTO.class,
                String.class, String.class, String.class, String.class, String.class, Long.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, request, prepay, prepay.getPaymentOrderId(), "default-app",
                        "H5", "127.0.0.1", "IDEMP-EXPIRED", new PaymentRouteDecisionDTO(),
                        prepay.getOrderNo(), "ROUTE-EXPIRED", "ATT-EXPIRED", "{}", "fingerprint", null));

        assertEquals(ErrorCode.PAYMENT_PREPAY_EXPIRED,
                ((BusinessException) exception.getCause()).getCode());
        verify(paymentMapper).lockPrepayCreation(prepay.getOrderNo());
        verify(paymentMapper, never()).updatePrepayToPaying(anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectNewActivePaymentBeforeRecordingRiskDecision() throws Exception {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setOrderNo("ORD-DUPLICATE-RISK");
        prepay.setPrepayOrderNo("PRE-DUPLICATE-RISK");
        prepay.setPaymentOrderId("PAY-DUPLICATE-RISK");
        PrepayOrderDTO activePrepay = boundPrepay();
        activePrepay.setPrepayOrderNo(prepay.getPrepayOrderNo());
        activePrepay.setActivePrepayCandidateCount(2);
        when(paymentMapper.findLatestActivePrepayByOrderNo(prepay.getOrderNo()))
                .thenReturn(activePrepay);
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId(prepay.getPaymentOrderId());
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("REVIEW");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "handleRiskDecision", PaymentSubmitRequestDTO.class, PrepayOrderDTO.class,
                PaymentDetailDTO.class, String.class, PaymentRiskDecisionResultDTO.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, new PaymentSubmitRequestDTO(), prepay,
                        detail, "wx_h5", riskDecision));

        assertEquals("active prepay orders for the same order are ambiguous",
                exception.getCause().getMessage());
        verify(paymentMapper).lockPrepayCreation(prepay.getOrderNo());
        verify(paymentMapper, never()).updatePaymentMethodAndChannel(
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldWriteConfiguredRoutingDecisionWhenSubmitPayment() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-300");
        prepay.setPaymentOrderId("PAY-300");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥6,800.00");
        prepay.setCustomerName("企业客户-晨星科技");
        when(paymentMapper.findPrepay("PRE-300")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-300");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
        submitResult.setGatewayCode("GW_BANK");
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
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-300"), anyString(), eq("银行转账"), eq("offline_bank"), eq("CHANNEL-300")))
                .thenReturn(1);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"SUCCESS\",\"channelTransactionNo\":\"CHANNEL-300\"}"),
                eq("处理中"), eq("info"))).thenReturn(1);
        when(paymentMapper.recordPaymentAttemptGateway(anyString(), eq("GW_BANK"))).thenReturn(1);

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

        ArgumentCaptor<String> boundAttemptNo = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).bindPaymentChannelSubmitResult(
                eq("PAY-300"), boundAttemptNo.capture(), eq("银行转账"),
                eq("offline_bank"), eq("CHANNEL-300"));
        verify(paymentMapper).updatePaymentReturnCodeSnapshot(
                "PAY-300", "BANK_TRANSFER_PENDING", "PAYMENT-CHANNEL-UNMAPPED",
                "未配置渠道返回码映射", "保留渠道原始事实并转人工判断",
                "未知", "是", null, Boolean.FALSE);
        assertEquals("offline_bank", result.getChannel());
        assertEquals("CHANNEL-300", result.getChannelTransactionNo());
        assertEquals("银行转账", result.getPaymentMethod());
        assertEquals("处理中", result.getLatestAttemptStatus());
        assertEquals("info", result.getLatestAttemptStatusType());
        org.junit.jupiter.api.Assertions.assertNull(result.getLatestAttemptCompletedAt());
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
                org.mockito.ArgumentMatchers.eq("warn"),
                org.mockito.ArgumentMatchers.matches("[0-9a-f]{64}"));
        org.mockito.InOrder routePersistenceOrder = org.mockito.Mockito.inOrder(paymentMapper);
        routePersistenceOrder.verify(paymentMapper).insertPaymentAttempt(
                eq(boundAttemptNo.getValue()), anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.isNull(), anyString(), anyString(), anyString());
        routePersistenceOrder.verify(paymentMapper).insertRouteRecord(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("{\"code\":\"SUCCESS\",\"channelTransactionNo\":\"CHANNEL-300\"}"),
                org.mockito.ArgumentMatchers.eq("处理中"),
                org.mockito.ArgumentMatchers.eq("info"));
        verify(paymentMapper).recordPaymentAttemptGateway(anyString(), eq("GW_BANK"));
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
        PrepayOrderDTO prepay = boundPrepay();
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
        detail.setAmount(prepay.getAmount());
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
        submitResult.setGatewayCode("GW_WX");
        submitResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-UNKNOWN-001"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-UNKNOWN-001")))
                .thenReturn(1);
        when(paymentMapper.markPaymentSubmitResultUnknown(
                eq("PAY-UNKNOWN-001"), anyString(), eq("微信支付"), eq("WX-UNKNOWN-001"))).thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException(
                        "jdbc:mysql://internal/payment password=submit-secret\ninvalid SQL"))
                .when(paymentMapper).updatePaymentAttemptSubmitResult(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("{\"code\":\"ACCEPTED\"}"),
                        org.mockito.ArgumentMatchers.eq("待回调"),
                        org.mockito.ArgumentMatchers.eq("warn"));
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"ACCEPTED\"}"),
                eq("结果未知，待查单"), eq("danger"))).thenReturn(1);
        when(paymentMapper.recordPaymentAttemptGateway(anyString(), eq("GW_WX"))).thenReturn(1);
        when(paymentMapper.updatePrepayStatusByPaymentOrderId(
                "PAY-UNKNOWN-001", "结果待确认", "danger")).thenReturn(1);

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
        verify(paymentMapper).markPaymentSubmitResultUnknown(
                eq("PAY-UNKNOWN-001"), anyString(), eq("微信支付"), eq("WX-UNKNOWN-001"));
        verify(paymentMapper).recordPaymentAttemptGateway(anyString(), eq("GW_WX"));
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                "PAY-UNKNOWN-001", "SUBMIT_EXCEPTION");
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                "PAY-UNKNOWN-001", "待支付", "warn");
    }

    @Test
    void shouldKeepInvalidChannelSubmitResultUnknownWithoutBindingExternalFacts() {
        PrepayOrderDTO prepay = boundPrepay();
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
        detail.setAmount(prepay.getAmount());
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
        when(paymentMapper.markPaymentSubmitResultUnknown(
                eq("PAY-INVALID-RESULT"), anyString(), eq("微信支付"),
                org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(1);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), org.mockito.ArgumentMatchers.isNull(),
                eq("结果未知，待查单"), eq("danger"))).thenReturn(1);

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
        verify(paymentMapper, never()).updatePaymentRiskStatus(
                eq("PAY-INVALID-RESULT"), anyString(), anyString());
        verify(paymentMapper).markPaymentSubmitResultUnknown(
                eq("PAY-INVALID-RESULT"), anyString(), eq("微信支付"),
                org.mockito.ArgumentMatchers.isNull());
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
    void shouldKeepTerminalChannelSubmitResultUnknownWithoutBindingAcceptedFacts() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-TERMINAL-RESULT");
        prepay.setPaymentOrderId("PAY-TERMINAL-RESULT");
        prepay.setOrderNo("ORD-TERMINAL-RESULT");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-TERMINAL-RESULT")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-TERMINAL-RESULT");
        detail.setOrderNo("ORD-TERMINAL-RESULT");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
        when(paymentMapper.findDetail("PAY-TERMINAL-RESULT")).thenReturn(detail);

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_WX");
        routeDecision.setRouteResult("家政微信优先 -> wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentMapper.findOrderNoByPrepayOrderNo("PRE-TERMINAL-RESULT"))
                .thenReturn("ORD-TERMINAL-RESULT");
        when(paymentMapper.updatePrepayToPaying("PRE-TERMINAL-RESULT")).thenReturn(1);

        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-TERMINAL-RESULT");
        submitResult.setAttemptStatus("成功");
        submitResult.setAttemptStatusType("success");
        submitResult.setChannelReturnCode("SUCCESS");
        submitResult.setResponsePayload("{\"code\":\"SUCCESS\"}");
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(submitResult);
        when(paymentMapper.markPaymentSubmitResultUnknown(
                eq("PAY-TERMINAL-RESULT"), anyString(), eq("微信支付"),
                eq("WX-TERMINAL-RESULT"))).thenReturn(1);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"SUCCESS\"}"),
                eq("结果未知，待查单"), eq("danger"))).thenReturn(1);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-TERMINAL-RESULT");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-TERMINAL-RESULT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verify(paymentMapper).markPaymentSubmitResultUnknown(
                eq("PAY-TERMINAL-RESULT"), anyString(), eq("微信支付"),
                eq("WX-TERMINAL-RESULT"));
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"SUCCESS\"}"),
                eq("结果未知，待查单"), eq("danger"));
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-TERMINAL-RESULT", "结果待确认", "danger");
        verify(paymentMapper, never()).bindPaymentChannelSubmitResult(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUBMIT"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectSubmitWhenSourceAppPaymentMethodNotAllowed() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-310");
        prepay.setPaymentOrderId("PAY-310");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-310")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-310");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-315");
        prepay.setPaymentOrderId("PAY-315");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-315")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-315");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-316");
        prepay.setPaymentOrderId("PAY-316");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-316")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-316");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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

        for (String invalidSwitchValue : new String[]{"开启 ", null}) {
            controlPolicy.setTokenAuthRequired(invalidSwitchValue);
            BusinessException invalidException = assertThrows(BusinessException.class,
                    () -> new PaymentServiceImpl(
                            paymentMapper,
                            paymentCallbackSignatureService,
                            paymentChannelRoutingService,
                            paymentChannelQueryService,
                            paymentChannelSubmitService)
                            .submit(request));
            assertEquals(ErrorCode.PAYMENT_ACCESS_TOKEN_INVALID, invalidException.getCode());
            verify(paymentMapper, never()).updatePrepayToPaying("PRE-316");
        }
    }

    @Test
    void shouldRejectSubmitWhenInterfaceMinuteRateLimitExceeded() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-318");
        prepay.setPaymentOrderId("PAY-318");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-318")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-318");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-320");
        prepay.setPaymentOrderId("PAY-320");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-320")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-320");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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

        for (String invalidSwitchValue : new String[]{"开启 ", null}) {
            controlPolicy.setStrictMode(invalidSwitchValue);
            BusinessException invalidException = assertThrows(BusinessException.class,
                    () -> new PaymentServiceImpl(
                            paymentMapper,
                            paymentCallbackSignatureService,
                            paymentChannelRoutingService,
                            paymentChannelQueryService,
                            paymentChannelSubmitService)
                            .submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_SELF_CHECK_BLOCKED, invalidException.getCode());
            verify(paymentMapper, never()).updatePrepayToPaying("PRE-320");
        }
        controlPolicy.setStrictMode("开启");
        controlPolicy.setSelfCheckStatus("pass");
        BusinessException aliasedSelfCheck = assertThrows(BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .submit(request));
        assertEquals(ErrorCode.PAYMENT_SUBMIT_SELF_CHECK_BLOCKED, aliasedSelfCheck.getCode());
        verify(paymentMapper, never()).updatePrepayToPaying("PRE-320");
    }

    @Test
    void shouldRejectSubmitWhenConcurrencyTokenStillActive() {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-330");
        prepay.setPaymentOrderId("PAY-330");
        prepay.setPayScene("HOME_CLEAN");
        prepay.setAmount("¥168.00");
        prepay.setCustomerName("张女士");
        when(paymentMapper.findPrepay("PRE-330")).thenReturn(prepay);

        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-330");
        detail.setStatus("PREPAY_CREATED");
        detail.setAmount(prepay.getAmount());
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
    void shouldRejectWhenInitialSubmitConcurrencyTokenInsertDoesNotCreateExactlyOneRow()
            throws Exception {
        when(paymentMapper.findSubmitConcurrencyToken("PRE-TOKEN-FAIL", "default-app"))
                .thenReturn(null);
        when(paymentMapper.insertSubmitConcurrencyToken(
                org.mockito.ArgumentMatchers.eq("PRE-TOKEN-FAIL"),
                org.mockito.ArgumentMatchers.eq("PAY-TOKEN-FAIL"),
                org.mockito.ArgumentMatchers.eq("default-app"),
                org.mockito.ArgumentMatchers.eq("IDEMP-TOKEN-FAIL"),
                org.mockito.ArgumentMatchers.eq("H5"),
                org.mockito.ArgumentMatchers.eq("127.0.0.1"),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(0);

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "occupySubmitConcurrencyToken",
                String.class, String.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, "PRE-TOKEN-FAIL", "PAY-TOKEN-FAIL", "default-app",
                        "H5", "127.0.0.1", "IDEMP-TOKEN-FAIL"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "payment submit concurrency token was not created atomically",
                exception.getCause().getMessage());
    }

    @Test
    void shouldRejectWhenSubmitConcurrencyTokenUpdateAffectsMultipleRows()
            throws Exception {
        PaymentSubmitConcurrencyTokenDTO token = new PaymentSubmitConcurrencyTokenDTO();
        token.setHolderIdempotencyKey("IDEMP-TOKEN-OLD");
        when(paymentMapper.findSubmitConcurrencyToken("PRE-TOKEN-MULTI", "default-app"))
                .thenReturn(token);
        when(paymentMapper.occupySubmitConcurrencyToken(
                "PRE-TOKEN-MULTI",
                "PAY-TOKEN-MULTI",
                "default-app",
                "IDEMP-TOKEN-MULTI",
                "H5",
                "127.0.0.1",
                120)).thenReturn(2);

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "occupySubmitConcurrencyToken",
                String.class, String.class, String.class, String.class, String.class, String.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, "PRE-TOKEN-MULTI", "PAY-TOKEN-MULTI", "default-app",
                        "H5", "127.0.0.1", "IDEMP-TOKEN-MULTI"));
        org.junit.jupiter.api.Assertions.assertEquals(
                "payment submit concurrency token update affected multiple rows",
                exception.getCause().getMessage());
    }

    @Test
    void shouldRejectWhenChannelSubmitResultBindingAffectsMultipleRows() throws Exception {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-BIND-MULTI");
        request.setPaymentMethod("微信支付");
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-BIND-MULTI");
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-BIND-MULTI"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-BIND-MULTI")))
                .thenReturn(2);

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "completeSubmit",
                PaymentSubmitRequestDTO.class, String.class, String.class, String.class,
                String.class, PaymentChannelSubmitResultDTO.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, request, "PAY-BIND-MULTI", "wx_h5", "ORD-BIND-MULTI",
                        "ATT-BIND-MULTI", submitResult));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment channel submit result binding affected multiple rows",
                exception.getCause().getMessage());
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectTerminalChannelSubmitStatusBeforePersistingAcceptedFacts() throws Exception {
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-TERMINAL-001");
        submitResult.setAttemptStatus("成功");
        submitResult.setAttemptStatusType("success");

        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "validatePaymentSubmitAcceptanceResult", PaymentChannelSubmitResultDTO.class);
        method.setAccessible(true);

        for (String terminalStatus : Arrays.asList("成功", "支付成功", " SUCCESS ")) {
            submitResult.setAttemptStatus(terminalStatus);
            java.lang.reflect.InvocationTargetException exception = assertThrows(
                    java.lang.reflect.InvocationTargetException.class,
                    () -> method.invoke(service, submitResult));
            org.junit.jupiter.api.Assertions.assertEquals(
                    ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                    ((BusinessException) exception.getCause()).getCode());
        }
        verify(paymentMapper, never()).bindPaymentChannelSubmitResult(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updatePaymentAttemptSubmitResult(
                anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectNullChannelSubmitResultBeforeFinalization() throws Exception {
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);
        java.lang.reflect.Method method = PaymentServiceImpl.class.getDeclaredMethod(
                "validatePaymentSubmitAcceptanceResult", PaymentChannelSubmitResultDTO.class);
        method.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(service, new Object[] {null}));

        org.junit.jupiter.api.Assertions.assertEquals(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                ((BusinessException) exception.getCause()).getCode());
        verify(paymentMapper, never()).bindPaymentChannelSubmitResult(
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void shouldReleaseSubmitConcurrencyTokenWhenClosePayment() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
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
    void shouldRejectCloseWhenAlreadySuccessfulPaymentFactsAreIncomplete() {
        PaymentDetailDTO detail = queryDetail("PAY-CLOSE-INCONSISTENT-SUCCESS", "SUCCESS");
        detail.setCreatedAt(null);
        when(paymentMapper.findDetail("PAY-CLOSE-INCONSISTENT-SUCCESS")).thenReturn(detail);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-INCONSISTENT-SUCCESS");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(anyString(), anyString());
    }

    @Test
    void shouldRejectManualCloseWhenFinalPaymentDetailCannotBeReadBack() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setPaymentOrderId("PAY-CLOSE-READBACK");
        detail.setOrderNo("ORD-CLOSE-READBACK");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-CLOSE-READBACK"))
                .thenReturn(detail, null);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-READBACK"))
                .thenReturn(1);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-READBACK");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        assertEquals("支付单已更新但读取失败", exception.getMessage());
    }

    @Test
    void shouldStopCloseWhenPaymentEventInsertDoesNotCreateExactlyOneRow() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setPaymentOrderId("PAY-CLOSE-EVENT-FAIL");
        detail.setOrderNo("ORD-CLOSE-EVENT-FAIL");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-CLOSE-EVENT-FAIL")).thenReturn(detail);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-EVENT-FAIL")).thenReturn(1);
        when(paymentMapper.insertEvent(
                org.mockito.ArgumentMatchers.any(), eq("PAYMENT_CLOSED"), eq("PAY-CLOSE-EVENT-FAIL"),
                eq("ORD-CLOSE-EVENT-FAIL"), org.mockito.ArgumentMatchers.any())).thenReturn(0);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-EVENT-FAIL");

        assertThrows(IllegalStateException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService).close(request, " 财务运营 "));
        verify(paymentMapper, never()).insertPaymentProcessLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFailWhenClosePrepayStatusCannotBeUpdated() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setPaymentOrderId("PAY-CLOSE-STATUS");
        detail.setOrderNo("ORD-CLOSE-STATUS");
        detail.setStatus("PREPAY_CREATED");
        when(paymentMapper.findDetail("PAY-CLOSE-STATUS")).thenReturn(detail);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-STATUS")).thenReturn(1);
        when(paymentMapper.updatePrepayStatusByPaymentOrderId(
                "PAY-CLOSE-STATUS", "已关闭", "danger")).thenReturn(0);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-STATUS");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService).close(request));
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-CLOSE-STATUS", "已关闭", "danger");
    }

    @Test
    void shouldRejectLocalCloseAfterPaymentWasSubmittedToChannel() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
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
    void shouldReleaseSubmitTokenWhenConcurrentCloseWinsBeforeThisRequest() {
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPrepayAmountBindingValid(true);
        initialDetail.setPaymentOrderId("PAY-CLOSE-RACE");
        initialDetail.setOrderNo("ORD-CLOSE-RACE");
        initialDetail.setStatus("PREPAY_CREATED");

        PaymentDetailDTO closedDetail = new PaymentDetailDTO();
        closedDetail.setPrepayAmountBindingValid(true);
        closedDetail.setPaymentOrderId("PAY-CLOSE-RACE");
        closedDetail.setOrderNo("ORD-CLOSE-RACE");
        closedDetail.setStatus("CLOSED");

        when(paymentMapper.findDetail("PAY-CLOSE-RACE")).thenReturn(initialDetail, closedDetail);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-RACE")).thenReturn(2);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-RACE");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .close(request);

        org.junit.jupiter.api.Assertions.assertEquals("CLOSED", result.getStatus());
        verify(paymentMapper).releaseSubmitConcurrencyToken(
                "PAY-CLOSE-RACE", "CLOSE_ALREADY_FINALIZED");
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectConcurrentCloseWhenPrepayBindingDrifts() {
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-CLOSE-BINDING-DRIFT");
        initialDetail.setOrderNo("ORD-CLOSE-BINDING-DRIFT");
        initialDetail.setStatus("PREPAY_CREATED");
        initialDetail.setPrepayAmountBindingValid(true);
        PaymentDetailDTO driftedDetail = new PaymentDetailDTO();
        driftedDetail.setPaymentOrderId("PAY-CLOSE-BINDING-DRIFT");
        driftedDetail.setOrderNo("ORD-CLOSE-BINDING-DRIFT");
        driftedDetail.setStatus("PREPAY_CREATED");
        driftedDetail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-CLOSE-BINDING-DRIFT"))
                .thenReturn(initialDetail, driftedDetail);
        when(paymentMapper.markUnsubmittedPaymentClosed("PAY-CLOSE-BINDING-DRIFT"))
                .thenReturn(0);

        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-BINDING-DRIFT");
        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).close(request));

        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldKeepSubmitTokenWhenLegacySubmittedPaymentIsAlreadyClosed() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
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
    void shouldRejectActiveQuerySettlementWhenStandardOutstandingChanges() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-SOURCE-DRIFT", "WAIT_CALLBACK");
        detail.setSourceKind("STANDARD_ORDER");
        detail.setCustomerName("张女士");
        when(paymentMapper.findDetail("PAY-QUERY-SOURCE-DRIFT")).thenReturn(detail, detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult(
                "PAY-QUERY-SOURCE-DRIFT", "WX-QUERY-SOURCE-DRIFT")).thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-QUERY-SOURCE-DRIFT"))
                .thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001")).thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-QUERY-001")).thenReturn(new BigDecimal("50.00"));
        when(paymentMapper.findCustomerNameByOrderNo("ORD-QUERY-001")).thenReturn("张女士");
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-SOURCE-DRIFT", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-SOURCE-DRIFT");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService).query(request));

        assertEquals("payment settlement standard order facts have changed", exception.getMessage());
        verify(paymentMapper, never()).updateOrderAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.any(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldSettlePaymentWhenActiveQueryConfirmsSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-001", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-QUERY-001", "SUCCESS");
        detail.setSourceKind("STANDARD_ORDER");
        settledDetail.setSourceKind("STANDARD_ORDER");
        detail.setCustomerName("张女士");
        settledDetail.setCustomerName("张女士");
        when(paymentMapper.findDetail("PAY-QUERY-001")).thenReturn(detail, detail, settledDetail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult("PAY-QUERY-001", "WX-QUERY-001"))
                .thenReturn(1);
        when(paymentMapper.finalizeLatestPaymentAttemptSuccess("PAY-QUERY-001")).thenReturn(1);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001"))
                .thenReturn(new BigDecimal("168.00"));
        when(paymentMapper.findPaidAmount("ORD-QUERY-001")).thenReturn(BigDecimal.ZERO);
        when(paymentMapper.findCustomerNameByOrderNo("ORD-QUERY-001")).thenReturn("张女士");
        when(paymentMapper.updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士"))
                .thenReturn(1);
        when(paymentMapper.updateBillAfterPayment(
                "BILL-QUERY-001", new BigDecimal("168.00"), "已结清", "success",
                "ORD-QUERY-001", "张女士", BigDecimal.ZERO)).thenReturn(1);
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

        verify(paymentMapper).finalizeLatestPaymentAttemptSuccess("PAY-QUERY-001");
        verify(paymentMapper).updatePrepayStatusByPaymentOrderId(
                "PAY-QUERY-001", "支付成功", "success");
        verify(paymentMapper).updateOrderAfterPayment(
                "ORD-QUERY-001", new BigDecimal("168.00"), "待履约", "info", BigDecimal.ZERO, "张女士");
        verify(paymentMapper).updateBillAfterPayment(
                "BILL-QUERY-001", new BigDecimal("168.00"), "已结清", "success",
                "ORD-QUERY-001", "张女士", BigDecimal.ZERO);
        verify(paymentMapper).insertEvent(
                anyString(),
                eq("PAYMENT_SUCCESS"),
                eq("PAY-QUERY-001"),
                eq("ORD-QUERY-001"),
                eq("{\"channel\":\"wx_h5\",\"payScene\":\"HOME_CLEAN\",\"source\":\"ACTIVE_QUERY\","
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
    void shouldRejectActiveQueryWhenSuccessUpdateAffectsMultipleRows() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-MULTI", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-MULTI")).thenReturn(detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentMapper.markPaymentSuccessFromChannelResult("PAY-QUERY-MULTI", "WX-QUERY-MULTI"))
                .thenReturn(2);
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-MULTI", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-MULTI");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService,
                        paymentEventDispatchService)
                        .query(request, "支付运营"));

        org.junit.jupiter.api.Assertions.assertEquals(
                "payment query success update affected multiple rows", exception.getMessage());
        verify(paymentMapper, never()).finalizeLatestPaymentAttemptSuccess("PAY-QUERY-MULTI");
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
    void shouldRejectActiveQueryWhenChannelMatchProjectionIsMissing() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-UNKNOWN-CHANNEL", "WAIT_CALLBACK");
        detail.setPaymentSubmissionChannelValid(null);
        when(paymentMapper.findDetail("PAY-QUERY-UNKNOWN-CHANNEL")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-UNKNOWN-CHANNEL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelQueryService);
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
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
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
    void shouldReturnCallbackWinnerWithoutOverwritingSuccessWithPendingQueryResult() {
        PaymentDetailDTO pending = queryDetail("PAY-QUERY-PENDING-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settled = queryDetail("PAY-QUERY-PENDING-RACE", "SUCCESS");
        when(paymentMapper.findDetail("PAY-QUERY-PENDING-RACE")).thenReturn(pending, settled);
        when(paymentChannelQueryService.query(pending))
                .thenReturn(queryResult("WAIT_CALLBACK", null, "GATEWAY_ACCESS_HTTP"));
        when(paymentMapper.updatePaymentReturnCodeSnapshot(
                eq("PAY-QUERY-PENDING-RACE"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-PENDING-RACE");
        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                paymentChannelQueryService, paymentChannelSubmitService).query(request);

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("WX-QUERY-001", result.getChannelTransactionNo());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRejectPendingQueryWhenCallbackWinnerBindsDifferentTransaction() {
        PaymentDetailDTO pending = queryDetail("PAY-QUERY-PENDING-CONFLICT", "WAIT_CALLBACK");
        PaymentDetailDTO settled = queryDetail("PAY-QUERY-PENDING-CONFLICT", "SUCCESS");
        when(paymentMapper.findDetail("PAY-QUERY-PENDING-CONFLICT")).thenReturn(pending, settled);
        when(paymentChannelQueryService.query(pending))
                .thenReturn(queryResult("WAIT_CALLBACK", "WX-OTHER", "GATEWAY_ACCESS_HTTP"));
        when(paymentMapper.updatePaymentReturnCodeSnapshot(
                eq("PAY-QUERY-PENDING-CONFLICT"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-PENDING-CONFLICT");
        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldRecordTerminalFailureQueryWithDangerStyle() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-TERMINAL-FAIL", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-QUERY-TERMINAL-FAIL")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-QUERY-TERMINAL-FAIL")).thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-TERMINAL-FAIL")).thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-TERMINAL-FAIL")).thenReturn(Collections.emptyList());
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("FAIL", "WX-QUERY-FAIL", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-TERMINAL-FAIL");

        new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request);

        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-TERMINAL-FAIL"), eq("wx_h5"), eq("FAIL"),
                org.mockito.ArgumentMatchers.contains("\"initiatedBy\":\"customer-self-service\""),
                org.mockito.ArgumentMatchers.contains("\"code\":\"SUCCESS\""),
                eq("已查单"), eq("danger"));
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
    void shouldRejectCallbackWinnerWithAliasedChannelAfterSuccessfulQuery() {
        PaymentDetailDTO querying = queryDetail("PAY-QUERY-CHANNEL-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settled = queryDetail("PAY-QUERY-CHANNEL-RACE", "SUCCESS");
        settled.setChannel("WX_H5");
        when(paymentMapper.findDetail("PAY-QUERY-CHANNEL-RACE"))
                .thenReturn(querying, querying, settled);
        when(paymentChannelQueryService.query(querying))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-001", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-CHANNEL-RACE");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-CHANNEL-RACE"), eq("WX_H5"), eq("QUERY_FAILED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectQueryTransactionWhenConcurrentCallbackBindsAnotherTransaction() {
        PaymentDetailDTO querying = queryDetail("PAY-QUERY-TXN-RACE", "WAIT_CALLBACK");
        PaymentDetailDTO settled = queryDetail("PAY-QUERY-TXN-RACE", "SUCCESS");
        settled.setChannelTransactionNo("WX-CALLBACK-WINNER");
        when(paymentMapper.findDetail("PAY-QUERY-TXN-RACE")).thenReturn(querying, settled);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(querying))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-CONFLICT", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-TXN-RACE");

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
                anyString(), eq("PAY-QUERY-TXN-RACE"), eq("wx_h5"), eq("QUERY_FAILED"),
                eq("{\"querySource\":\"CHANNEL_QUERY\","
                        + "\"initiatedBy\":\"customer-self-service\"}"),
                eq("{\"code\":\"PAYMENT-1026\",\"message\":\"channel query failed\"}"),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
        verify(paymentMapper, never()).updateBillAfterPayment(
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
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
                anyString(), org.mockito.ArgumentMatchers.any(), anyString(), anyString(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldNotReturnClosedAsConfirmedWhenChannelReportsSuccess() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-003", "CLOSED");
        detail.setLatestAttemptStatus("结果未知，待查单");
        when(paymentMapper.findDetail("PAY-QUERY-003")).thenReturn(detail);
        when(paymentMapper.lockPrepayCreation("ORD-QUERY-001")).thenReturn("ORD-QUERY-001");
        when(paymentChannelQueryService.query(detail))
                .thenReturn(queryResult("SUCCESS", "WX-QUERY-003", "GATEWAY_ACCESS_HTTP"));

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-003");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService)
                .query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(paymentMapper).markPaymentSuccessFromChannelResult(
                "PAY-QUERY-003", "WX-QUERY-003");
        verify(paymentMapper).insertNotifyLog(
                anyString(), eq("PAY-QUERY-003"), eq("wx_h5"), eq("QUERY_FAILED"),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                eq("查单失败"), eq("danger"));
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
    }

    @Test
    void shouldRejectClosedPaymentQueryWithTerminalLatestAttempt() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-CLOSED-TERMINAL", "CLOSED");
        detail.setLatestAttemptStatus("FAILED");
        when(paymentMapper.findDetail("PAY-QUERY-CLOSED-TERMINAL")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-CLOSED-TERMINAL");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING, exception.getCode());
        detail.setLatestAttemptStatus("结果未知，待查单");
        detail.setLatestAttemptCompletedAt("2026-09-12 10:00:00");
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () ->
                        new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                                paymentChannelRoutingService, paymentChannelQueryService,
                                paymentChannelSubmitService).query(request)).getCode());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldReturnUnsubmittedClosedPaymentWithoutQueryingChannel() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-CLOSED-UNSUBMITTED", "CLOSED");
        when(paymentMapper.findDetail("PAY-QUERY-CLOSED-UNSUBMITTED")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-QUERY-CLOSED-UNSUBMITTED"))
                .thenReturn(Collections.emptyList());
        when(paymentMapper.findNotifyLogs("PAY-QUERY-CLOSED-UNSUBMITTED"))
                .thenReturn(Collections.emptyList());
        when(paymentMapper.findEventItems("PAY-QUERY-CLOSED-UNSUBMITTED"))
                .thenReturn(Collections.emptyList());

        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-CLOSED-UNSUBMITTED");

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .query(request);

        org.junit.jupiter.api.Assertions.assertEquals("CLOSED", result.getStatus());
        verifyNoInteractions(paymentChannelQueryRateLimitService, paymentChannelQueryService);
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    @Test
    void shouldNotRepeatSettlementWhenAnotherQueryAlreadyWon() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-004", "WAIT_CALLBACK");
        PaymentDetailDTO settledDetail = queryDetail("PAY-QUERY-004", "SUCCESS");
        settledDetail.setChannelTransactionNo("WX-QUERY-004");
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

        verify(paymentMapper, never()).updateLatestPaymentAttemptStatus(
                anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUCCESS"), anyString(), anyString(), anyString());
        verify(paymentEventDispatchService, never()).publishPaymentSuccess(anyString(), anyString());
        verify(paymentMapper, never()).markPaymentSuccessFromChannelResult(anyString(), anyString());
    }

    private PaymentDetailDTO queryDetail(String paymentOrderId, String status) {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("VIRTUAL");
        detail.setPaymentOrderId(paymentOrderId);
        detail.setBillNo("BILL-QUERY-001");
        detail.setOrderNo("ORD-QUERY-001");
        detail.setChannel("wx_h5");
        detail.setPaymentSubmissionChannelValid(true);
        detail.setPayScene("HOME_CLEAN");
        detail.setAmount("¥168.00");
        detail.setCreatedAt("2026-09-12 09:59:00");
        detail.setStatus(status);
        if ("SUCCESS".equals(status)) {
            detail.setStatusType("success");
        }
        if ("WAIT_CALLBACK".equals(status)) {
            detail.setLatestAttemptStatus("待回调");
        } else if ("SUCCESS".equals(status)) {
            detail.setChannelTransactionNo("WX-QUERY-001");
            detail.setSuccessAt("2026-09-12 10:00:00");
            detail.setLatestAttemptStatus("成功");
            detail.setLatestAttemptStatusType("success");
            detail.setLatestAttemptCompletedAt("2026-09-12 10:00:00");
            detail.setPaymentFactTimelineValid(true);
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
    void shouldThrowNotFoundWhenPaymentDetailMissing() {
        when(paymentMapper.findDetail("PAY-DETAIL-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .detail("PAY-DETAIL-MISSING")
        );

        org.junit.jupiter.api.Assertions.assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, exception.getCode());
        org.junit.jupiter.api.Assertions.assertEquals("支付单不存在", exception.getMessage());
    }

    @Test
    void shouldRejectCaseVariantPaymentIdentifierAtUserEntries() {
        PaymentDetailDTO matchedByCollation = queryDetail("PAY-EXACT-001", "CLOSED");
        matchedByCollation.setChannelTransactionNo(null);
        matchedByCollation.setLatestAttemptStatus(null);
        when(paymentMapper.findDetail("pay-exact-001")).thenReturn(matchedByCollation);
        PaymentServiceImpl service = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService);

        BusinessException detailRejected = assertThrows(BusinessException.class,
                () -> service.detail("pay-exact-001"));
        assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, detailRejected.getCode());

        com.abc123.hsp.dto.PaymentQueryRequestDTO queryRequest =
                new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("pay-exact-001");
        BusinessException queryRejected = assertThrows(BusinessException.class,
                () -> service.query(queryRequest));
        assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, queryRejected.getCode());

        com.abc123.hsp.dto.PaymentCloseRequestDTO closeRequest =
                new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        closeRequest.setPaymentOrderId("pay-exact-001");
        BusinessException closeRejected = assertThrows(BusinessException.class,
                () -> service.close(closeRequest));
        assertEquals(ErrorCode.PAYMENT_ORDER_NOT_FOUND, closeRejected.getCode());
        verify(paymentMapper, never()).markUnsubmittedPaymentClosed("pay-exact-001");
        verify(paymentMapper, never()).releaseSubmitConcurrencyToken(
                "pay-exact-001", "CLOSE_ALREADY_FINALIZED");
    }

    @Test
    void shouldRejectPaymentDetailWhenSuccessfulFactsAreIncomplete() {
        PaymentDetailDTO detail = queryDetail("PAY-DETAIL-INCONSISTENT-SUCCESS", "SUCCESS");
        detail.setCreatedAt(null);
        when(paymentMapper.findDetail("PAY-DETAIL-INCONSISTENT-SUCCESS")).thenReturn(detail);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper,
                        paymentCallbackSignatureService,
                        paymentChannelRoutingService,
                        paymentChannelQueryService,
                        paymentChannelSubmitService)
                        .detail("PAY-DETAIL-INCONSISTENT-SUCCESS"));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
        verify(paymentMapper, never()).findRouteLogs(anyString());
    }

    @Test
    void shouldRejectNonSuccessfulPaymentDetailWithoutVerifiedPrepayBinding() {
        PaymentServiceImpl service = new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                paymentChannelRoutingService, paymentChannelQueryService, paymentChannelSubmitService);
        for (String status : Arrays.asList("PREPAY_CREATED", "WAIT_CALLBACK", "RISK_REVIEW", "CLOSED", "FAIL")) {
            String paymentOrderId = "PAY-UNVERIFIED-" + status;
            PaymentDetailDTO detail = queryDetail(paymentOrderId, status);
            detail.setPrepayAmountBindingValid(false);
            when(paymentMapper.findDetail(paymentOrderId)).thenReturn(detail);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> service.detail(paymentOrderId));
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
            detail.setPrepayAmountBindingValid(null);
            assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                    assertThrows(BusinessException.class, () -> service.detail(paymentOrderId)).getCode());
            verify(paymentMapper, never()).findRouteLogs(paymentOrderId);
        }
    }

    @Test
    void shouldRejectQueryBeforeChannelCallWhenPrepayBindingIsUnverified() {
        PaymentDetailDTO detail = queryDetail("PAY-QUERY-UNVERIFIED-PREPAY", "WAIT_CALLBACK");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-QUERY-UNVERIFIED-PREPAY")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentQueryRequestDTO request = new com.abc123.hsp.dto.PaymentQueryRequestDTO();
        request.setPaymentOrderId("PAY-QUERY-UNVERIFIED-PREPAY");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).query(request));
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        verifyNoInteractions(paymentChannelQueryService);
    }

    @Test
    void shouldRejectCloseBeforeStateChangeWhenPrepayBindingIsUnverified() {
        PaymentDetailDTO detail = queryDetail("PAY-CLOSE-UNVERIFIED-PREPAY", "PREPAY_CREATED");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-CLOSE-UNVERIFIED-PREPAY")).thenReturn(detail);
        com.abc123.hsp.dto.PaymentCloseRequestDTO request = new com.abc123.hsp.dto.PaymentCloseRequestDTO();
        request.setPaymentOrderId("PAY-CLOSE-UNVERIFIED-PREPAY");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).close(request));
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        verify(paymentMapper, never()).markUnsubmittedPaymentClosed("PAY-CLOSE-UNVERIFIED-PREPAY");
    }

    @Test
    void shouldRejectPendingCallbackBeforeStateChangeWhenPrepayBindingIsUnverified() {
        PaymentDetailDTO detail = queryDetail("PAY-CALLBACK-UNVERIFIED-PREPAY", "WAIT_CALLBACK");
        detail.setPrepayAmountBindingValid(false);
        when(paymentMapper.findDetail("PAY-CALLBACK-UNVERIFIED-PREPAY")).thenReturn(detail);
        PaymentCallbackRequestDTO request = new PaymentCallbackRequestDTO();
        request.setPaymentOrderId("PAY-CALLBACK-UNVERIFIED-PREPAY");
        request.setTradeStatus("PROCESSING");
        request.setChannelTransactionNo("WX-UNVERIFIED-PREPAY");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).callback("wx_h5", request));
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, exception.getCode());
        verify(paymentMapper, never()).markPaymentPendingFromCallback(
                "PAY-CALLBACK-UNVERIFIED-PREPAY", "WX-UNVERIFIED-PREPAY");
    }

    @Test
    void shouldRejectWaitingPaymentDetailWithoutChannelSubmissionFact() {
        PaymentDetailDTO detail = queryDetail("PAY-DETAIL-UNBACKED-WAIT", "WAIT_CALLBACK");
        detail.setPaymentSubmissionChannelValid(false);
        when(paymentMapper.findDetail("PAY-DETAIL-UNBACKED-WAIT")).thenReturn(detail);
        PaymentServiceImpl service = new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                paymentChannelRoutingService, paymentChannelQueryService, paymentChannelSubmitService);

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.detail("PAY-DETAIL-UNBACKED-WAIT")).getCode());
        detail.setPaymentSubmissionChannelValid(true);
        detail.setLatestAttemptStatus(null);
        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING,
                assertThrows(BusinessException.class, () -> service.detail("PAY-DETAIL-UNBACKED-WAIT")).getCode());
        verify(paymentMapper, never()).findRouteLogs(anyString());
    }

    @Test
    void shouldExposeWaitingPaymentDetailWithChannelSubmissionFact() {
        PaymentDetailDTO detail = queryDetail("PAY-DETAIL-BACKED-WAIT", "WAIT_CALLBACK");
        when(paymentMapper.findDetail("PAY-DETAIL-BACKED-WAIT")).thenReturn(detail);

        PaymentDetailDTO result = new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                paymentChannelRoutingService, paymentChannelQueryService,
                paymentChannelSubmitService).detail("PAY-DETAIL-BACKED-WAIT");

        assertEquals("WAIT_CALLBACK", result.getStatus());
        assertEquals("待回调", result.getLatestAttemptStatus());
        verify(paymentMapper).findRouteLogs("PAY-DETAIL-BACKED-WAIT");
    }

    @Test
    void shouldRejectSuccessfulPaymentDetailWithUnknownSourceKind() {
        PaymentDetailDTO detail = queryDetail("PAY-DETAIL-UNKNOWN-SOURCE", "SUCCESS");
        detail.setSourceKind("UNKNOWN");
        when(paymentMapper.findDetail("PAY-DETAIL-UNKNOWN-SOURCE")).thenReturn(detail);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService)
                        .detail("PAY-DETAIL-UNKNOWN-SOURCE"));

        assertEquals("payment success facts are inconsistent", exception.getMessage());
        verify(paymentMapper, never()).findRouteLogs(anyString());
    }

    @Test
    void shouldRejectSuccessfulPaymentDetailWhenSourceTypeChanges() {
        PaymentDetailDTO standard = queryDetail("PAY-DETAIL-STANDARD-LOST", "SUCCESS");
        standard.setSourceKind("STANDARD_ORDER");
        when(paymentMapper.findDetail("PAY-DETAIL-STANDARD-LOST")).thenReturn(standard);

        IllegalStateException standardException = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService)
                        .detail("PAY-DETAIL-STANDARD-LOST"));
        assertEquals("payment success facts are inconsistent", standardException.getMessage());

        PaymentDetailDTO virtual = queryDetail("PAY-DETAIL-VIRTUAL-CHANGED", "SUCCESS");
        when(paymentMapper.findDetail("PAY-DETAIL-VIRTUAL-CHANGED")).thenReturn(virtual);
        when(paymentMapper.findOrderAmount("ORD-QUERY-001")).thenReturn(new BigDecimal("168.00"));
        IllegalStateException virtualException = assertThrows(IllegalStateException.class, () ->
                new PaymentServiceImpl(
                        paymentMapper, paymentCallbackSignatureService, paymentChannelRoutingService,
                        paymentChannelQueryService, paymentChannelSubmitService)
                        .detail("PAY-DETAIL-VIRTUAL-CHANGED"));
        assertEquals("payment success facts are inconsistent", virtualException.getMessage());
        verify(paymentMapper, never()).findRouteLogs(anyString());
    }

    @Test
    void shouldReportNonPositiveHistoricalSuccessAmountAsInconsistentFacts() {
        for (String amount : Arrays.asList("¥0.00", "¥-5.00", null)) {
            PaymentDetailDTO detail = queryDetail("PAY-DETAIL-INVALID-AMOUNT", "SUCCESS");
            detail.setAmount(amount);
            when(paymentMapper.findDetail("PAY-DETAIL-INVALID-AMOUNT")).thenReturn(detail);

            IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                    new PaymentServiceImpl(
                            paymentMapper,
                            paymentCallbackSignatureService,
                            paymentChannelRoutingService,
                            paymentChannelQueryService,
                            paymentChannelSubmitService)
                            .detail("PAY-DETAIL-INVALID-AMOUNT"));

            assertEquals("payment success facts are inconsistent", exception.getMessage());
        }
    }

    @Test
    void shouldNormalizeNullPaymentDetailCollections() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-EMPTY");
        detail.setPrepayAmountBindingValid(true);
        detail.setCustomerName("张女士");
        when(paymentMapper.findDetail("PAY-DETAIL-EMPTY")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-DETAIL-EMPTY")).thenReturn(null);
        when(paymentMapper.findNotifyLogs("PAY-DETAIL-EMPTY")).thenReturn(null);
        when(paymentMapper.findEventItems("PAY-DETAIL-EMPTY")).thenReturn(null);

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .detail("PAY-DETAIL-EMPTY");

        org.junit.jupiter.api.Assertions.assertNotNull(result.getRouteLogs());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getNotifyLogs());
        org.junit.jupiter.api.Assertions.assertNotNull(result.getEventLogs());
        org.junit.jupiter.api.Assertions.assertTrue(result.getRouteLogs().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(result.getNotifyLogs().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(result.getEventLogs().isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals("CHAIN-PAY-DETAIL-EMPTY", result.getBusinessChainId());
    }

    @Test
    void shouldIgnoreNullRowsInPaymentDetailCollections() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-NULL-ROW");
        detail.setPrepayAmountBindingValid(true);
        when(paymentMapper.findDetail("PAY-DETAIL-NULL-ROW")).thenReturn(detail);
        when(paymentMapper.findRouteLogs("PAY-DETAIL-NULL-ROW")).thenReturn(Arrays.asList(null, "ROUTE-1"));
        when(paymentMapper.findNotifyLogs("PAY-DETAIL-NULL-ROW")).thenReturn(Arrays.asList(null, "NOTIFY-1"));
        when(paymentMapper.findEventItems("PAY-DETAIL-NULL-ROW")).thenReturn(Arrays.asList(null, "EVENT-1"));

        PaymentDetailDTO result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .detail("PAY-DETAIL-NULL-ROW");

        org.junit.jupiter.api.Assertions.assertEquals(Arrays.asList("ROUTE-1"), result.getRouteLogs());
        org.junit.jupiter.api.Assertions.assertEquals(Arrays.asList("NOTIFY-1"), result.getNotifyLogs());
        org.junit.jupiter.api.Assertions.assertEquals(Arrays.asList("EVENT-1"), result.getEventLogs());
    }

    @Test
    void shouldKeepLatestAttemptContextWhenLoadingPaymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-DETAIL-100");
        detail.setPrepayAmountBindingValid(true);
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
        when(paymentMapper.findAll(query))
                .thenReturn(java.util.Arrays.asList((PaymentListItemDTO) null, item));
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
        org.junit.jupiter.api.Assertions.assertEquals("CHAIN-PAY-001", result.getItems().get(0).getBusinessChainId());
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
        item.setPayScene("HOME_CLEAN");
        item.setCustomerName("张女士");
        item.setAmount("¥88.00");
        item.setPaymentMethod("微信支付");
        item.setChannel("wx_h5");
        item.setChannelTransactionNo("WX-001");
        item.setStatus("SUCCESS");
        item.setStatusType("success");
        item.setLatestAttemptStatus("成功");
        item.setLatestAttemptStatusType("success");
        item.setLatestAttemptCompletedAt("2026-07-29 10:00:05");
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
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("业务链路"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("CHAIN-PAY-001"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("支付状态样式"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("创建时间,成功事实异常类型,预付归属异常类型\n"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("订单号,支付场景,客户名称"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("HOME_CLEAN"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"成功\",\"success\",\"2026-07-29 10:00:05\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"张*\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.endsWith(",\"\"\n"));
        org.junit.jupiter.api.Assertions.assertFalse(csv.contains("张女士"));

        item.setPayScene(null);
        item.setPrepayBindingIssue("MULTIPLE_PREPAY_BINDINGS");
        String diagnosticCsv = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .exportCsv(query);
        org.junit.jupiter.api.Assertions.assertTrue(diagnosticCsv.contains("预付归属异常类型\n"));
        org.junit.jupiter.api.Assertions.assertTrue(diagnosticCsv.contains("MULTIPLE_PREPAY_BINDINGS"));
        org.junit.jupiter.api.Assertions.assertFalse(diagnosticCsv.contains("HOME_CLEAN"));
        org.junit.jupiter.api.Assertions.assertEquals("张女士", item.getCustomerName());
        verify(paymentMapper, times(2)).findAllForExport(query);
    }

    @Test
    void shouldKeepSuccessFactIssuesInPaymentCsvRows() {
        PaymentListQueryDTO query = new PaymentListQueryDTO();
        PaymentListItemDTO mismatch = new PaymentListItemDTO();
        mismatch.setPaymentOrderId("PAY-MISMATCH");
        mismatch.setStatus("SUCCESS");
        mismatch.setSuccessFactIssue("SUCCESS_FACT_MISMATCH");
        PaymentListItemDTO missingTime = new PaymentListItemDTO();
        missingTime.setPaymentOrderId("PAY-MISSING-TIME");
        missingTime.setStatus("SUCCESS");
        missingTime.setSuccessFactIssue("SUCCESS_TIME_MISSING");
        when(paymentMapper.findAllForExport(query)).thenReturn(Arrays.asList(mismatch, missingTime));

        String csv = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService)
                .exportCsv(query);

        String[] rows = csv.split("\n");
        org.junit.jupiter.api.Assertions.assertEquals(3, rows.length);
        org.junit.jupiter.api.Assertions.assertTrue(rows[1].startsWith("\"PAY-MISMATCH\","));
        org.junit.jupiter.api.Assertions.assertTrue(rows[1].endsWith(",\"SUCCESS_FACT_MISMATCH\",\"\""));
        org.junit.jupiter.api.Assertions.assertTrue(rows[2].startsWith("\"PAY-MISSING-TIME\","));
        org.junit.jupiter.api.Assertions.assertTrue(rows[2].endsWith(",\"SUCCESS_TIME_MISSING\",\"\""));
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
                repeat("P", 64) + " ", null, null, null, null);
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
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-RISK-001")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-001");
        request.setPaymentMethod("微信支付");
        request.setSourceAppId("housekeeping-app-web");
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

        verify(paymentRiskControlService, times(1)).evaluateSubmitRisk(argThat(riskRequest ->
                "housekeeping-app-web".equals(riskRequest.getSourceAppId())
                        && "payment-core".equals(riskRequest.getSourceSystem())));
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, times(1)).updatePaymentRiskStatus("PAY-RISK-001", "RISK_REVIEW", "warn");
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
    void shouldRejectRiskSubmitWhenPrepayCannotBeReadBack() {
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setPrepayOrderNo("PRE-RISK-READBACK");
        prepayOrder.setPaymentOrderId("PAY-RISK-READBACK");
        prepayOrder.setOrderNo("ORD-RISK-READBACK");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-RISK-READBACK");
        paymentDetail.setOrderNo("ORD-RISK-READBACK");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("REVIEW");

        when(paymentMapper.findPrepay("PRE-RISK-READBACK"))
                .thenReturn(prepayOrder, (PrepayOrderDTO) null);
        paymentDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-RISK-READBACK")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-READBACK");
        request.setPaymentMethod("微信支付");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");

        BusinessException exception = assertThrows(BusinessException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService).submit(request));

        assertEquals(ErrorCode.SYSTEM_ERROR, exception.getCode());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStopRiskFlowWhenPrepayStatusUpdateDoesNotAffectExactlyOneRow() {
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setPrepayOrderNo("PRE-RISK-STATUS-FAIL");
        prepayOrder.setPaymentOrderId("PAY-RISK-STATUS-FAIL");
        prepayOrder.setOrderNo("ORD-RISK-STATUS-FAIL");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-RISK-STATUS-FAIL");
        paymentDetail.setOrderNo("ORD-RISK-STATUS-FAIL");
        paymentDetail.setStatus("CREATED");
        paymentDetail.setChannelTransactionNo("");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");

        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("REVIEW");
        riskDecision.setDecisionType("warn");
        riskDecision.setReviewNo("REVIEW-STATUS-FAIL");
        riskDecision.setRiskTag("风险复核");
        riskDecision.setMessage("需要复核");

        when(paymentMapper.findPrepay("PRE-RISK-STATUS-FAIL")).thenReturn(prepayOrder);
        paymentDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-RISK-STATUS-FAIL")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class))).thenReturn(riskDecision);
        when(paymentMapper.updatePaymentMethodAndChannel(
                eq("PAY-RISK-STATUS-FAIL"), eq("PRE-RISK-STATUS-FAIL"), eq("微信支付"), eq("wx_h5"),
                org.mockito.ArgumentMatchers.any())).thenReturn(0);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-RISK-STATUS-FAIL");
        request.setPaymentMethod("微信支付");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService).submit(request));
        org.junit.jupiter.api.Assertions.assertEquals(
                "PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException", exception.getMessage());
        verify(paymentMapper, never()).updatePaymentRiskStatus(
                "PAY-RISK-STATUS-FAIL", "RISK_REVIEW", "warn");
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStopBeforePaymentWritesWhenRiskServiceRejectsItsResponse() {
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
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
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(paymentChannelSubmitService, never()).ensureAdapterAvailable(anyString());
        verify(paymentChannelSubmitService, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldContinueSubmitAfterRiskReviewApproved() {
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-RISK-APPROVED-001")).thenReturn(paymentDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-RISK-APPROVED-001")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-RISK-APPROVED-001"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-APPROVED-001")))
                .thenReturn(1);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"SUCCESS\"}"), eq("待回调"), eq("warn")))
                .thenReturn(1);

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
        verify(paymentMapper, never()).updatePaymentRiskStatus("PAY-RISK-APPROVED-001", "RISK_REVIEW", "warn");
        org.junit.jupiter.api.Assertions.assertEquals("PRE-RISK-APPROVED-001", result.getPrepayOrderNo());
    }

    @Test
    void shouldNotRegressFastSuccessCallbackWhenSubmitResultCompletes() {
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setPrepayOrderNo("PRE-FAST-CALLBACK");
        prepayOrder.setPaymentOrderId("PAY-FAST-CALLBACK");
        prepayOrder.setOrderNo("ORD-FAST-CALLBACK");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");
        prepayOrder.setCashierStatus("支付成功");

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-FAST-CALLBACK");
        initialDetail.setOrderNo("ORD-FAST-CALLBACK");
        initialDetail.setStatus("PREPAY_CREATED");

        PaymentDetailDTO successDetail = queryDetail("PAY-FAST-CALLBACK", "SUCCESS");
        successDetail.setOrderNo("ORD-FAST-CALLBACK");
        successDetail.setChannel("wx_h5");
        successDetail.setChannelTransactionNo("WX-FAST-CALLBACK");
        successDetail.setLatestAttemptStatus("成功");
        successDetail.setLatestAttemptStatusType("success");
        successDetail.setLatestAttemptCompletedAt("2026-09-13 15:00:00");
        successDetail.setSuccessAt("2026-09-13 15:00:00");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-FAST-CALLBACK");
        submitResult.setGatewayCode("GW_WX");
        submitResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");

        when(paymentMapper.findPrepay("PRE-FAST-CALLBACK"))
                .thenReturn(prepayOrder, prepayOrder);
        initialDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-FAST-CALLBACK"))
                .thenReturn(initialDetail, successDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-FAST-CALLBACK")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(submitResult);
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-FAST-CALLBACK"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-FAST-CALLBACK")))
                .thenReturn(0);
        when(paymentMapper.isLatestPaymentAttemptForOrder(eq("PAY-FAST-CALLBACK"), anyString()))
                .thenReturn(true);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"ACCEPTED\"}"), eq("待回调"), eq("warn")))
                .thenReturn(1);
        when(paymentMapper.recordPaymentAttemptGateway(anyString(), eq("GW_WX"))).thenReturn(1);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-FAST-CALLBACK");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-FAST-CALLBACK");

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

        org.junit.jupiter.api.Assertions.assertEquals("支付成功", result.getCashierStatus());
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"ACCEPTED\"}"), eq("待回调"), eq("warn"));
        verify(paymentMapper).recordPaymentAttemptGateway(anyString(), eq("GW_WX"));
        verify(paymentMapper, never()).updatePaymentReturnCodeSnapshot(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUBMIT"), anyString(), anyString(), anyString());
        verify(paymentMapper, never()).insertNotifyLog(
                anyString(), eq("PAY-FAST-CALLBACK"), anyString(), eq("SUBMIT"),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectFastSuccessBoundToChannelAlias() {
        assertFastSuccessBindingRejected("WX_H5", "微信支付");
    }

    @Test
    void shouldRejectFastSuccessBoundToOtherPaymentMethod() {
        assertFastSuccessBindingRejected("wx_h5", "支付宝");
    }

    private void assertFastSuccessBindingRejected(String boundChannel, String boundPaymentMethod) {
        PrepayOrderDTO prepay = boundPrepay();
        prepay.setPrepayOrderNo("PRE-FAST-CHANNEL-ALIAS");
        prepay.setPaymentOrderId("PAY-FAST-CHANNEL-ALIAS");
        prepay.setAmount("¥168.00");
        when(paymentMapper.findPrepay(prepay.getPrepayOrderNo())).thenReturn(prepay);
        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId(prepay.getPaymentOrderId());
        initialDetail.setAmount(prepay.getAmount());
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO successDetail = queryDetail(prepay.getPaymentOrderId(), "SUCCESS");
        successDetail.setChannel(boundChannel);
        successDetail.setPaymentMethod(boundPaymentMethod);
        when(paymentMapper.findDetail(prepay.getPaymentOrderId()))
                .thenReturn(initialDetail, successDetail);
        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentMapper.updatePrepayToPaying(prepay.getPrepayOrderNo())).thenReturn(1);
        PaymentChannelSubmitResultDTO acceptedResult = new PaymentChannelSubmitResultDTO();
        acceptedResult.setChannelTransactionNo("WX-QUERY-001");
        acceptedResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        acceptedResult.setAttemptStatus("待回调");
        acceptedResult.setAttemptStatusType("warn");
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(acceptedResult);
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");

        BusinessException exception = assertThrows(BusinessException.class, () ->
                new PaymentServiceImpl(paymentMapper, paymentCallbackSignatureService,
                        paymentChannelRoutingService, paymentChannelQueryService,
                        paymentChannelSubmitService).submit(request));

        assertEquals(ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED, exception.getCode());
        verify(paymentMapper, never()).updatePaymentAttemptSubmitResult(
                anyString(), anyString(), eq("待回调"), eq("warn"));
    }

    @Test
    void shouldNotTreatAnotherAttemptsFastSuccessAsThisSubmitResult() {
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setPrepayOrderNo("PRE-STALE-FAST-SUCCESS");
        prepayOrder.setPaymentOrderId("PAY-STALE-FAST-SUCCESS");
        prepayOrder.setOrderNo("ORD-STALE-FAST-SUCCESS");
        prepayOrder.setAmount("168.00");
        PaymentDetailDTO initialDetail = queryDetail("PAY-STALE-FAST-SUCCESS", "PREPAY_CREATED");
        initialDetail.setOrderNo("ORD-STALE-FAST-SUCCESS");
        PaymentDetailDTO successDetail = queryDetail("PAY-STALE-FAST-SUCCESS", "SUCCESS");
        successDetail.setOrderNo("ORD-STALE-FAST-SUCCESS");
        successDetail.setChannel("wx_h5");
        successDetail.setChannelTransactionNo("WX-STALE-FAST-SUCCESS");
        successDetail.setLatestAttemptStatus("成功");
        successDetail.setLatestAttemptStatusType("success");
        successDetail.setLatestAttemptCompletedAt("2026-09-13 15:00:00");
        successDetail.setSuccessAt("2026-09-13 15:00:00");
        PaymentRouteDecisionDTO decision = new PaymentRouteDecisionDTO();
        decision.setChannelCode("wx_h5");
        decision.setRouteRule("RULE_HOME_CLEAN");
        decision.setRouteResult("命中微信 H5");
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-STALE-FAST-SUCCESS");
        submitResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");

        when(paymentMapper.findPrepay("PRE-STALE-FAST-SUCCESS")).thenReturn(prepayOrder);
        when(paymentMapper.findDetail("PAY-STALE-FAST-SUCCESS"))
                .thenReturn(initialDetail, successDetail, successDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(decision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-STALE-FAST-SUCCESS")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(submitResult);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-STALE-FAST-SUCCESS");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-STALE-FAST-SUCCESS");

        assertThrows(RuntimeException.class, () -> new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService,
                paymentEventDispatchService,
                paymentRiskControlService,
                paymentOpsConfigService).submit(request));
        verify(paymentMapper, never()).updatePaymentAttemptSubmitResult(
                anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, never()).recordPaymentAttemptGateway(
                anyString(), org.mockito.ArgumentMatchers.any());
        verify(paymentMapper, times(2)).isLatestPaymentAttemptForOrder(
                eq("PAY-STALE-FAST-SUCCESS"), anyString());
    }

    @Test
    void shouldNotRegressFastSuccessWhenUnknownResultRecoveryRuns() {
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setPrepayOrderNo("PRE-FAST-RECOVERY");
        prepayOrder.setPaymentOrderId("PAY-FAST-RECOVERY");
        prepayOrder.setOrderNo("ORD-FAST-RECOVERY");
        prepayOrder.setCustomerName("张女士");
        prepayOrder.setAmount("168.00");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO initialDetail = new PaymentDetailDTO();
        initialDetail.setPaymentOrderId("PAY-FAST-RECOVERY");
        initialDetail.setOrderNo("ORD-FAST-RECOVERY");
        initialDetail.setStatus("PREPAY_CREATED");
        PaymentDetailDTO successDetail = queryDetail("PAY-FAST-RECOVERY", "SUCCESS");
        successDetail.setOrderNo("ORD-FAST-RECOVERY");
        successDetail.setChannel("wx_h5");
        successDetail.setChannelTransactionNo("WX-FAST-RECOVERY");
        successDetail.setLatestAttemptStatus("成功");
        successDetail.setLatestAttemptStatusType("success");
        successDetail.setLatestAttemptCompletedAt("2026-09-13 15:00:00");
        successDetail.setSuccessAt("2026-09-13 15:00:00");

        PaymentRouteDecisionDTO routeDecision = new PaymentRouteDecisionDTO();
        routeDecision.setChannelCode("wx_h5");
        routeDecision.setRouteRule("RULE_HOME_CLEAN");
        routeDecision.setRouteResult("命中微信 H5");
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision("PASS");
        PaymentChannelSubmitResultDTO submitResult = new PaymentChannelSubmitResultDTO();
        submitResult.setChannelTransactionNo("WX-FAST-RECOVERY");
        submitResult.setResponsePayload("{\"code\":\"ACCEPTED\"}");
        submitResult.setAttemptStatus("待回调");
        submitResult.setAttemptStatusType("warn");

        when(paymentMapper.findPrepay("PRE-FAST-RECOVERY")).thenReturn(prepayOrder);
        initialDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-FAST-RECOVERY"))
                .thenReturn(initialDetail, successDetail);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(
                org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.updatePrepayToPaying("PRE-FAST-RECOVERY")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(submitResult);
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-FAST-RECOVERY"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-FAST-RECOVERY")))
                .thenReturn(1);
        org.mockito.Mockito.doThrow(new IllegalStateException("snapshot write failed"))
                .when(paymentMapper).updatePaymentReturnCodeSnapshot(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        when(paymentMapper.markPaymentSubmitResultUnknown(
                eq("PAY-FAST-RECOVERY"), anyString(), eq("微信支付"),
                eq("WX-FAST-RECOVERY"))).thenReturn(0);
        when(paymentMapper.isLatestPaymentAttemptForOrder(eq("PAY-FAST-RECOVERY"), anyString()))
                .thenReturn(true);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"ACCEPTED\"}"), eq("待回调"), eq("warn")))
                .thenReturn(1);

        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo("PRE-FAST-RECOVERY");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setIdempotencyKey("IDEMP-FAST-RECOVERY");

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                new PaymentServiceImpl(
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
                "PAYMENT_SUBMIT_TRANSACTION_FAILED:IllegalStateException", exception.getMessage());
        verify(paymentMapper).markPaymentSubmitResultUnknown(
                eq("PAY-FAST-RECOVERY"), anyString(), eq("微信支付"),
                eq("WX-FAST-RECOVERY"));
        verify(paymentMapper).updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"ACCEPTED\"}"), eq("待回调"), eq("warn"));
        verify(paymentMapper, never()).updatePrepayStatusByPaymentOrderId(
                eq("PAY-FAST-RECOVERY"), eq("结果待确认"), eq("danger"));
        verify(paymentMapper, never()).updatePaymentRiskStatus(
                eq("PAY-FAST-RECOVERY"), anyString(), anyString());
        verify(paymentMapper, never()).insertEvent(
                anyString(), eq("PAYMENT_SUBMIT"), anyString(), anyString(), anyString());
    }

    @Test
    void shouldApplyOpsConfigDefaultsWhenSubmitRequestMissesMethodAndChannel() {
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
        when(paymentMapper.findDetail("PAY-OPS-001")).thenReturn(paymentDetail);
        when(paymentOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(snapshot);
        when(paymentChannelRoutingService.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(routeDecision);
        when(paymentRiskControlService.evaluateSubmitRisk(org.mockito.ArgumentMatchers.any(PaymentRiskDecisionRequestDTO.class)))
                .thenReturn(riskDecision);
        when(paymentMapper.existsPaymentAttemptByIdempotencyKey("PRE-OPS-001|微信支付|wx_h5")).thenReturn(false);
        when(paymentMapper.updatePrepayToPaying("PRE-OPS-001")).thenReturn(1);
        when(paymentChannelSubmitService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(submitResult);
        when(paymentMapper.bindPaymentChannelSubmitResult(
                eq("PAY-OPS-001"), anyString(), eq("微信支付"), eq("wx_h5"), eq("WX-OPS-001")))
                .thenReturn(1);
        when(paymentMapper.updatePaymentAttemptSubmitResult(
                anyString(), eq("{\"code\":\"SUCCESS\"}"), eq("待回调"), eq("warn")))
                .thenReturn(1);
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
        PrepayOrderDTO prepayOrder = boundPrepay();
        prepayOrder.setAmount("¥168.00");
        prepayOrder.setPrepayOrderNo("PRE-OPS-INVALID-METHOD");
        prepayOrder.setPaymentOrderId("PAY-OPS-INVALID-METHOD");
        prepayOrder.setPayScene("HOME_CLEAN");

        PaymentDetailDTO paymentDetail = new PaymentDetailDTO();
        paymentDetail.setPaymentOrderId("PAY-OPS-INVALID-METHOD");
        paymentDetail.setStatus("CREATED");

        PaymentOpsConfigSnapshotDTO snapshot = new PaymentOpsConfigSnapshotDTO();
        snapshot.setDefaultPayMethod(String.join("", Collections.nCopies(33, "M")));

        when(paymentMapper.findPrepay("PRE-OPS-INVALID-METHOD")).thenReturn(prepayOrder);
        paymentDetail.setAmount(prepayOrder.getAmount());
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
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
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
        PrepayOrderDTO prepayOrder = boundPrepay();
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
        paymentDetail.setAmount(prepayOrder.getAmount());
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

    @Test
    void shouldUseDefaultQueryWhenListingPaymentsWithNullQuery() {
        when(paymentMapper.findAll(org.mockito.ArgumentMatchers.any(PaymentListQueryDTO.class)))
                .thenReturn(Collections.emptyList());
        when(paymentMapper.count(org.mockito.ArgumentMatchers.any(PaymentListQueryDTO.class))).thenReturn(0L);

        PageResultDTO<PaymentListItemDTO> result = new PaymentServiceImpl(
                paymentMapper,
                paymentCallbackSignatureService,
                paymentChannelRoutingService,
                paymentChannelQueryService,
                paymentChannelSubmitService).list(null);

        org.junit.jupiter.api.Assertions.assertEquals(0, result.getTotal());
        org.mockito.ArgumentCaptor<PaymentListQueryDTO> captor =
                org.mockito.ArgumentCaptor.forClass(PaymentListQueryDTO.class);
        verify(paymentMapper).findAll(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(1, captor.getValue().getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(20, captor.getValue().getPageSize());
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder(value.length() * times);
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
