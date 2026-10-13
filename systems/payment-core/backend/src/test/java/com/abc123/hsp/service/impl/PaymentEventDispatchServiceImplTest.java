package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.common.PaymentEventAmqpSignature;
import com.abc123.hsp.common.PaymentEventHttpSignature;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

/**
 * 支付事件下游投递服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventDispatchServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private RefundMapper refundMapper;
    @Mock
    private RefundChannelSubmitService refundChannelSubmitService;

    @BeforeEach
    void defaultPaymentSuccessEvent() {
        org.mockito.Mockito.lenient()
                .when(paymentEventMapper.findByEventNoForUpdate(any()))
                .thenAnswer(invocation -> paymentEvent(invocation.getArgument(0), 0));
        org.mockito.Mockito.lenient()
                .when(paymentEventMapper.markPaymentSuccessClearingAccepted(any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentEventMapper.markPaymentSuccessHttpCompleted(any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentEventMapper.markPublishFailed(any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentEventMapper.markPublishDeadLetter(any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(refundMapper.insertOperationLog(any()))
                .thenReturn(1);
        org.mockito.Mockito.lenient()
                .when(paymentMapper.findOrderAmount("ORD-001"))
                .thenReturn(new BigDecimal("168.00"));
        org.mockito.Mockito.lenient()
                .when(paymentMapper.findPaidAmount("ORD-001"))
                .thenReturn(new BigDecimal("168.00"));
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(any(), any(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void shouldDispatchPaymentSuccessInNewTransactionAfterBusinessCommit() throws Exception {
        Method method = PaymentEventDispatchServiceImpl.class.getMethod(
                "publishPaymentSuccess", String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        Assertions.assertNotNull(transactional);
        Assertions.assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void shouldNormalizeBusinessChainIdForDownstreamPayloadBuilders() throws Exception {
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://clearing",
                "http://accounting",
                "ACT10003",
                restTemplate);
        Method method = PaymentEventDispatchServiceImpl.class.getDeclaredMethod(
                "businessChainId", String.class);
        method.setAccessible(true);

        Assertions.assertEquals("CHAIN-PAY-001", method.invoke(service, "  PAY-001  "));
        Assertions.assertNull(method.invoke(service, "  "));
        Assertions.assertNull(method.invoke(service, new Object[] {null}));
    }

    @Test
    void shouldDispatchRefundInNewTransactionAfterApprovalCommit() throws Exception {
        Method method = PaymentEventDispatchServiceImpl.class.getMethod(
                "publishRefundSubmit", String.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        Assertions.assertNotNull(transactional);
        Assertions.assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void shouldNotDispatchPaymentSuccessEventAgainWhenAlreadySucceeded() {
        PaymentEventListItemDTO event = paymentEvent("EVT-SUCCESS-INITIAL", 1);
        event.setPublishStatus("SUCCESS");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-SUCCESS-INITIAL")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-SUCCESS-INITIAL", "PAY-001");

        verify(paymentMapper, never()).findDetail(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
    }

    @Test
    void shouldRejectMismatchedPaymentSuccessEventBeforeCallingDownstream() {
        PaymentEventListItemDTO event = paymentEvent("EVT-MISMATCHED-INITIAL", 0);
        event.setPaymentOrderId("PAY-OTHER");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-MISMATCHED-INITIAL")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-MISMATCHED-INITIAL", "PAY-001");

        verify(paymentMapper, never()).findDetail(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
    }

    @Test
    void shouldDispatchRefundSubmitAndPersistAcceptedFact() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-001");
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "RCH-REF-001")).thenReturn(1);
        when(refundMapper.bindRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "RCH-REF-001")).thenReturn(1);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService).submit(request);
        verify(refundMapper).bindRefundAttemptAccepted("RFA-001", "RCH-REF-001");
        verify(refundMapper).bindRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "RCH-REF-001");
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT".equals(log.getActionCode())
                        && "refund-operator".equals(log.getOperatorName())
                        && log.getOperationRemark().contains("RCH-REF-001")));
    }

    @Test
    void shouldStopRefundDispatchWhenSubmitLogDoesNotTakeEffect() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-001");
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "RCH-REF-001")).thenReturn(1);
        when(refundMapper.bindRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "RCH-REF-001")).thenReturn(1);
        when(refundMapper.insertOperationLog(any())).thenReturn(0);

        Assertions.assertThrows(IllegalStateException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator "));
        verify(paymentEventMapper, never()).markPublishSuccess("EVTR-001");
    }

    @Test
    void shouldRepublishFailedRefundAndKeepInitiatorInSubmitLog() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-RETRY-001");
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "RCH-REF-RETRY-001")).thenReturn(1);
        when(refundMapper.bindRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "RCH-REF-RETRY-001")).thenReturn(1);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .republish(" EVTR-001 ", " finance-ops ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService).submit(request);
        verify(refundMapper).bindRefundAttemptAccepted("RFA-001", "RCH-REF-RETRY-001");
        verify(refundMapper).bindRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "RCH-REF-RETRY-001");
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT".equals(log.getActionCode())
                        && "finance-ops".equals(log.getOperatorName())
                        && log.getOperationRemark().contains("RCH-REF-RETRY-001")));
    }

    @Test
    void shouldTreatTerminalRefundSubmitResultAsUnknownBeforePersistingAcceptedFact() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-TERMINAL-001");
        result.setStatus("SUCCESS");
        result.setStatusType("success");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.markRefundAttemptUnknown("RFA-001")).thenReturn(1);
        when(refundMapper.recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5"))
                .thenReturn(1);
        when(paymentEventMapper.markPublishUnknown("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).bindRefundAttemptAccepted(any(), any());
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any(), any());
        verify(paymentEventMapper, never()).markPublishSuccess("EVTR-001");
        verify(refundMapper).markRefundAttemptUnknown("RFA-001");
        verify(refundMapper).recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5");
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
    }

    @Test
    void shouldKeepRefundProcessingAndStopRetryWhenChannelResultIsUnknown() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("timeout", true));
        when(refundMapper.markRefundAttemptUnknown("RFA-001")).thenReturn(1);
        when(refundMapper.recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5"))
                .thenReturn(1);
        when(paymentEventMapper.markPublishUnknown("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper).markRefundAttemptUnknown("RFA-001");
        verify(refundMapper).recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5");
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishSuccess("EVTR-001");
        verify(refundMapper, never()).updateRefundStatus(any(), any(), any(), any(), anyBoolean());
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT_UNKNOWN".equals(log.getActionCode())
                        && "refund-operator".equals(log.getOperatorName())));
    }

    @Test
    void shouldRetryWhenGatewayRejectsBeforeCallingUpstream() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("not configured", false));
        when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper).markRefundAttemptFailed("RFA-001");
        verify(refundMapper, never()).recordUnknownRefundSubmitChannel(any(), any(), any(), any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishUnknown("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT_FAILED".equals(log.getActionCode())
                && "refund-operator".equals(log.getOperatorName())));

    }

    @Test
    void shouldStopRefundDispatchWhenRefundAttemptFailureUpdateAffectsMultipleRows() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("not configured", false));
        when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(2);

        Assertions.assertThrows(IllegalStateException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldRejectInvalidDispatchOperatorBeforeReadingEvent() {
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.publishRefundSubmit("EVTR-001", "RFA-001", " "));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.republish("EVTR-001", String.join("", java.util.Collections.nCopies(65, "A"))));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.republish("EVTR-001", "operator\nforged"));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.republish("EVTR-001", "operator\n"));

        org.mockito.Mockito.verifyNoInteractions(
                paymentEventMapper, refundMapper, refundChannelSubmitService);
    }

    @Test
    void shouldRejectInvalidDispatchIdentifiersBeforeReadingEvent() {
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3);
        String oversizedIdentifier = String.join("", java.util.Collections.nCopies(65, "A"));

        IllegalArgumentException blankEventNo = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.republish(" ", "finance-ops"));
        IllegalArgumentException oversizedEventNo = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishPaymentSuccess(oversizedIdentifier, "PAY-001"));
        IllegalArgumentException controlEventNo = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishRefundSubmit("EVTR-001\n", "REF-001", "finance-ops"));
        IllegalArgumentException oversizedPaymentOrderId = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishPaymentSuccess("EVT-001", oversizedIdentifier));
        String oversizedAfterTrimIdentifier = String.join("", java.util.Collections.nCopies(64, "A")) + " ";
        IllegalArgumentException oversizedAfterTrimEventNo = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.republish(oversizedAfterTrimIdentifier, "finance-ops"));
        IllegalArgumentException controlRefundOrderId = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishRefundSubmit("EVTR-001", "REF-001\n", "finance-ops"));

        Assertions.assertEquals("事件号不能为空", blankEventNo.getMessage());
        Assertions.assertEquals("事件号长度不能超过64字符", oversizedEventNo.getMessage());
        Assertions.assertEquals("事件号不能包含控制字符", controlEventNo.getMessage());
        Assertions.assertEquals("支付单号长度不能超过64字符", oversizedPaymentOrderId.getMessage());
        Assertions.assertEquals("事件号长度不能超过64字符", oversizedAfterTrimEventNo.getMessage());
        Assertions.assertEquals("退款尝试号不能包含控制字符", controlRefundOrderId.getMessage());
        org.mockito.Mockito.verifyNoInteractions(
                paymentMapper, paymentEventMapper, refundMapper, refundChannelSubmitService);
    }

    @Test
    void shouldRejectInconsistentRefundSubmitSnapshotBeforeCallingChannel() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setPaymentOrderId("PAY-OTHER");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT_FAILED".equals(log.getActionCode())));
    }

    @Test
    void shouldRejectPersistedRefundAttemptChannelWithTrailingWhitespaceBeforeCallingChannel() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelCode("wx_h5 ");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator");

        Assertions.assertFalse(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(refundMapper, never()).recordUnknownRefundSubmitChannel(any(), any(), any(), any());
        verify(refundMapper).markRefundAttemptFailed("RFA-001");
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldNotSubmitRefundForWrongEventDestinationOrTopic() {
        for (String wrongField : java.util.Arrays.asList("downstream", "topic")) {
            PaymentEventListItemDTO event = refundEvent("PENDING", 0);
            event.setDownstreamSystem("downstream".equals(wrongField) ? "other-system" : "gateway-access");
            event.setEventTopic("topic".equals(wrongField)
                    ? "payment.refund.other.v1" : "payment.refund.submitted.v1");
            when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);

            Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                    paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                    .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
            verify(refundMapper, never()).findChannelSubmitRequestByAttemptNo(any());
            verify(refundChannelSubmitService, never()).submit(any());
            verify(paymentEventMapper, never()).markPublishSuccess(any());
        }
    }

    @Test
    void shouldNotDispatchRefundEventWithUnsupportedPublishStatus() {
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("PROCESSING", 0));

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundMapper, never()).findChannelSubmitRequestByAttemptNo(any());
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldQuarantineRefundEventWhenRefundIsNoLongerProcessing() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        event.setDownstreamSystem("gateway-access");
        event.setEventTopic("payment.refund.submitted.v1");
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setRefundStatus("SUCCESS");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldRequireReviewForUnknownEventWhenRefundIsNoLongerProcessing() {
        PaymentEventListItemDTO event = refundEvent("UNKNOWN", 1);
        event.setDownstreamSystem("gateway-access");
        event.setEventTopic("payment.refund.submitted.v1");
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setRefundStatus("FAIL");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        Assertions.assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldNotSubmitRefundAgainWhenEventAlreadySucceeded() {
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("SUCCESS", 1));
        when(refundMapper.isMatchingSuccessfulRefundSubmitEvent("EVTR-001", "RFA-001"))
                .thenReturn(true);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldNotTrustSuccessfulRefundEventWithoutAcceptedTrace() {
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("SUCCESS", 1));

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundMapper).isMatchingSuccessfulRefundSubmitEvent("EVTR-001", "RFA-001");
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldFailRefundEventBeforeChannelWhenAttemptIsNoLongerCurrent() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(null);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .republish("EVTR-001", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(refundMapper, never()).insertOperationLog(any());
    }

    @Test
    void shouldRejectDirectRepublishWhenRefundResultIsUnknown() {
        PaymentEventListItemDTO event = refundEvent("UNKNOWN", 1);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001"))
                .thenReturn(refundSubmitRequest());

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .republish("EVTR-001", " payment-event-scheduler "));

        Assertions.assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
    }

    @Test
    void shouldCompleteUnknownEventWhenAcceptedTraceHasBeenReconciled() {
        PaymentEventListItemDTO event = refundEvent("UNKNOWN", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelRefundNo("CR-RECONCILED-001");
        request.setSubmittedChannelCode(request.getChannelCode());
        request.setOrderChannelRefundNo("CR-RECONCILED-001");
        request.setChannelSubmitRecorded(true);
        request.setAttemptStatus("ACCEPTED");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishUnknown(any());
    }

    @Test
    void shouldNotConfirmAcceptedAttemptWhenRefundOrderHasNoChannelTrace() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelRefundNo("RCH-ATTEMPT-ONLY");
        request.setAttemptStatus("ACCEPTED");
        request.setSubmittedChannelCode("wx_h5");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldNotSubmitAgainWhenRefundOrderAlreadyHasChannelAcceptance() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setSubmittedChannelCode("wx_h5");
        request.setOrderChannelRefundNo("RCH-ORDER-ACCEPTED");
        request.setChannelSubmitRecorded(true);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);
        org.mockito.Mockito.lenient().when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("unexpected second submit", false));
        org.mockito.Mockito.lenient().when(refundMapper.insertOperationLog(any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(1);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundChannelSubmitService, never()).submit(any());
        verify(refundMapper, never()).markRefundAttemptFailed(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldNotSubmitAgainWhenRefundOrderOnlyHasChannelMarker() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setSubmittedChannelCode("wx_h5");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldNotSubmitAgainWhenAttemptChannelRefundNoIsBlank() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelRefundNo("  ");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);
        org.mockito.Mockito.lenient().when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("unexpected second submit", false));
        org.mockito.Mockito.lenient().when(refundMapper.insertOperationLog(any())).thenReturn(1);
        org.mockito.Mockito.lenient().when(refundMapper.markRefundAttemptFailed("RFA-001")).thenReturn(1);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldKeepUnknownEventForPartialOrderChannelMarker() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setSubmittedChannelCode("wx_h5");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("UNKNOWN", 1));
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        Assertions.assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
    }

    @Test
    void shouldNotConfirmAcceptedTraceThroughChannelCodeAlias() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelRefundNo("RCH-ACCEPTED");
        request.setAttemptStatus("ACCEPTED");
        request.setSubmittedChannelCode("WX_H5");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVTR-001")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVTR-001")).thenReturn(1);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldNotConfirmAcceptedTraceWithPaddedPersistedChannel() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelCode("wx_h5 ");
        request.setSubmittedChannelCode("wx_h5 ");
        request.setChannelRefundNo("RCH-ACCEPTED");
        request.setOrderChannelRefundNo("RCH-ACCEPTED");
        request.setChannelSubmitRecorded(true);
        request.setAttemptStatus("ACCEPTED");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        Assertions.assertFalse(new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        verify(refundMapper, never()).markRefundAttemptFailed(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldRequireReviewForUnknownEventWithUnmatchedAcceptedTrace() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setChannelRefundNo("RCH-ATTEMPT-ONLY");
        request.setAttemptStatus("ACCEPTED");
        request.setSubmittedChannelCode("wx_h5");
        request.setOrderChannelRefundNo("RCH-OTHER");
        request.setChannelSubmitRecorded(true);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("UNKNOWN", 1));
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);

        BusinessException exception = Assertions.assertThrows(BusinessException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVTR-001", "RFA-001", "refund-operator"));
        Assertions.assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishUnknown(any());
        verify(refundMapper, never()).markRefundAttemptUnknown(any());
        verify(refundChannelSubmitService, never()).submit(any());
    }

    @Test
    void shouldCompleteEventWithoutCallingChannelWhenAcceptedTraceAlreadyExists() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setSubmittedChannelCode("wx_h5");
        request.setChannelRefundNo("RCH-REF-001");
        request.setOrderChannelRefundNo("RCH-REF-001");
        request.setChannelSubmitRecorded(true);
        request.setAttemptStatus("ACCEPTED");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("FAILED", 1));
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any(), any());
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
    }

    @Test
    void shouldRejectOversizedChannelRefundNoBeforePersistingAcceptedTrace() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo(new String(new char[129]).replace('\0', 'R'));
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("PENDING", 0));
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.markRefundAttemptUnknown("RFA-001")).thenReturn(1);
        when(refundMapper.recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5"))
                .thenReturn(1);
        when(paymentEventMapper.markPublishUnknown("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any(), any());
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldRejectChannelRefundNoControlCharacterBeforePersistingAcceptedTrace() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-001\n");
        result.setStatus("PROCESSING");
        result.setStatusType("warn");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("PENDING", 0));
        when(refundMapper.findChannelSubmitRequestByAttemptNo("RFA-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.markRefundAttemptUnknown("RFA-001")).thenReturn(1);
        when(refundMapper.recordUnknownRefundSubmitChannel("EVTR-001", "REF-001", "RFA-001", "wx_h5"))
                .thenReturn(1);
        when(paymentEventMapper.markPublishUnknown("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "RFA-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any(), any());
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldPublishPaymentSuccessToAmqpWithTraceableMessageProperties() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1"
        );
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        service.publishPaymentSuccess("EVT-001", "PAY-001");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq("payment.trade"),
                eq("payment.success.v1"),
                payloadCaptor.capture(),
                processorCaptor.capture(),
                any(CorrelationData.class)
        );
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"eventNo\":\"EVT-001\""));
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"eventType\":\"PAYMENT_SUCCESS\""));
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"schemaVersion\":\"1\""));
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"paymentOrderId\":\"PAY-001\""));
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"accountNo\":\"ACT10003\""));

        Message message = processorCaptor.getValue().postProcessMessage(new Message(
                String.valueOf(payloadCaptor.getValue()).getBytes(StandardCharsets.UTF_8), new MessageProperties()));
        Assertions.assertEquals("EVT-001", message.getMessageProperties().getMessageId());
        Assertions.assertEquals("EVT-001", message.getMessageProperties().getCorrelationId());
        Assertions.assertEquals("PAYMENT_SUCCESS", message.getMessageProperties().getHeaders().get("eventType"));
        Assertions.assertEquals("1", message.getMessageProperties().getHeaders().get("schemaVersion"));
        Assertions.assertEquals("PAY-001", message.getMessageProperties().getHeaders().get("paymentOrderId"));
        Assertions.assertEquals(PaymentEventAmqpSignature.sign(message.getBody(), "local-payment-amqp-test-secret"),
                message.getMessageProperties().getHeaders().get(PaymentEventAmqpSignature.HEADER_NAME));
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted(any());
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted(any());
        verify(restTemplate, never()).postForEntity(
                any(), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldNotMarkAmqpEventPublishedWhenBrokerReturnsUnroutableMessage() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE",
                    "payment.trade", "payment.success.v1"));
            correlationData.getFuture().set(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("payment.trade"), eq("payment.success.v1"),
                any(), any(MessagePostProcessor.class), any(CorrelationData.class));

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper, "ACT10003", 3, rabbitTemplate,
                new ObjectMapper(), "payment.trade", "payment.success.v1");
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        service.publishPaymentSuccess("EVT-001", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-001");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldNotMarkAmqpEventPublishedWhenBrokerNegativelyConfirms() {
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().set(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq("payment.trade"), eq("payment.success.v1"),
                any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper, "ACT10003", 3, rabbitTemplate,
                new ObjectMapper(), "payment.trade", "payment.success.v1");
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");

        service.publishPaymentSuccess("EVT-001", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-001");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldNotMarkAmqpEventPublishedBeforeConfirmTimeout() {
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        org.mockito.Mockito.doAnswer(invocation -> null).when(rabbitTemplate)
                .convertAndSend(eq("payment.trade"), eq("payment.success.v1"),
                        any(), any(MessagePostProcessor.class), any(CorrelationData.class));
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper, "ACT10003", 3, rabbitTemplate,
                new ObjectMapper(), "payment.trade", "payment.success.v1", 1L);
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");

        service.publishPaymentSuccess("EVT-001", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-001");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldCapPaymentSuccessAmqpPublisherConfirmTimeout() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1",
                120000L
        );
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        service.publishPaymentSuccess("EVT-001", "PAY-001");

        Assertions.assertEquals(60000L, getField(service, "publisherConfirmTimeoutMs"));
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldUseDefaultPaymentSuccessAmqpPublisherConfirmTimeoutWhenNonPositive() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1",
                0L
        );
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        service.publishPaymentSuccess("EVT-001", "PAY-001");

        Assertions.assertEquals(5000L, getField(service, "publisherConfirmTimeoutMs"));
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldMarkFailedWhenAmqpPublishFails() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("ORD-001");
        event.setPublishStatus("FAILED");
        event.setPaymentSuccessHttpStage("PENDING");
        event.setRetryCount(0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-001")).thenReturn(event);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(eq("payment.trade"), eq("payment.success.v1"), any(),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1"
        );
        setField(service, "paymentSuccessAmqpSecret", "local-payment-amqp-test-secret");
        boolean success = service.republish("EVT-001", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishFailed("EVT-001");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldRejectMissingAmqpAccountBeforePublishing() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-AMQP-CONFIG-MISSING", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-AMQP-CONFIG-MISSING")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                " ",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1"
        ).publishPaymentSuccess("EVT-AMQP-CONFIG-MISSING", "PAY-001");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(paymentEventMapper).markPublishFailed("EVT-AMQP-CONFIG-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMQP-CONFIG-MISSING");
    }

    @Test
    void shouldRejectMissingAmqpSecretBeforePublishing() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-AMQP-SECRET-MISSING", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-AMQP-SECRET-MISSING")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper, "ACT10003", 3,
                rabbitTemplate, new ObjectMapper(), "payment.trade", "payment.success.v1"
        ).publishPaymentSuccess("EVT-AMQP-SECRET-MISSING", "PAY-001");

        verify(rabbitTemplate, never()).convertAndSend(any(), any(), any(),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(paymentEventMapper).markPublishFailed("EVT-AMQP-SECRET-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMQP-SECRET-MISSING");
    }

    @Test
    void shouldPublishPaymentSuccessToClearingAndAccounting() throws Exception {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        verify(restTemplate).postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(restTemplate).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).postForEntity(
                any(String.class), payloadCaptor.capture(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        Assertions.assertTrue(new ObjectMapper().writeValueAsString(payloadCaptor.getAllValues().get(0))
                .contains("\"payScene\":\"HOME_CLEAN\""));
        verify(paymentEventMapper).markPaymentSuccessClearingAccepted("EVT-001");
        verify(paymentEventMapper).markPaymentSuccessHttpCompleted("EVT-001");
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-001");
    }

    @Test
    void shouldRejectRawOverlongDownstreamUrlBeforeHttpDispatch() {
        String overlongUrl = "http://" + repeat('a', 2041) + " ";
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                overlongUrl,
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-URL-LIMIT", "PAY-001");

        verify(restTemplate, never()).postForEntity(
                any(String.class), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(paymentEventMapper).markPublishFailed("EVT-URL-LIMIT");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-URL-LIMIT");
    }

    @Test
    void shouldRejectMissingPaymentSuccessAmountBeforeCallingDownstream() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setAmount(null);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.findByEventNo("EVT-AMOUNT-MISSING"))
                .thenReturn(paymentEvent("EVT-AMOUNT-MISSING", 0));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-AMOUNT-MISSING", "PAY-001");

        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-AMOUNT-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMOUNT-MISSING");
    }

    @Test
    void shouldRejectZeroPaymentSuccessAmountBeforeCallingDownstream() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setAmount("¥0.00");
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.findByEventNo("EVT-AMOUNT-ZERO"))
                .thenReturn(paymentEvent("EVT-AMOUNT-ZERO", 0));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-AMOUNT-ZERO", "PAY-001");

        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper).markPublishFailed("EVT-AMOUNT-ZERO");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMOUNT-ZERO");
    }

    @Test
    void shouldRejectPaymentSuccessAmountWithMoreThanTwoRawDecimalPlaces() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setAmount("¥100.000");
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.findByEventNo("EVT-AMOUNT-SCALE")).thenReturn(
                paymentEvent("EVT-AMOUNT-SCALE", 0));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-AMOUNT-SCALE", "PAY-001");

        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-AMOUNT-SCALE");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMOUNT-SCALE");
    }

    @Test
    void shouldAdvanceFailureStateWhenRepublishedPaymentAmountIsMalformed() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setAmount("invalid-amount");
        PaymentEventListItemDTO event = paymentEvent("EVT-AMOUNT-MALFORMED", 2);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-AMOUNT-MALFORMED"))
                .thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.findByEventNo("EVT-AMOUNT-MALFORMED")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-AMOUNT-MALFORMED", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper).markPublishDeadLetter("EVT-AMOUNT-MALFORMED");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-AMOUNT-MALFORMED");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMOUNT-MALFORMED");
    }

    @Test
    void shouldMarkFailedWhenAccountingReturnsNon2xx() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-001");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("ORD-001");
        event.setPublishStatus("FAILED");
        event.setPaymentSuccessHttpStage("PENDING");
        event.setRetryCount(0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-001")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(response("0", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-001", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPaymentSuccessClearingAccepted("EVT-001");
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted("EVT-001");
        verify(paymentEventMapper).markPublishFailed("EVT-001");
    }

    @Test
    void shouldResumeAtAccountingWhenClearingWasAlreadyAccepted() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-ACCOUNTING-RETRY", 1);
        event.setPaymentSuccessHttpStage("CLEARING_ACCEPTED");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-ACCOUNTING-RETRY")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(paymentEventMapper.markPublishSuccess("EVT-ACCOUNTING-RETRY")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-ACCOUNTING-RETRY", " payment-event-scheduler ");

        Assertions.assertTrue(success);
        verify(restTemplate, never()).postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(restTemplate).postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted(any());
        verify(paymentEventMapper).markPaymentSuccessHttpCompleted("EVT-ACCOUNTING-RETRY");
        verify(paymentEventMapper).markPublishSuccess("EVT-ACCOUNTING-RETRY");
    }

    @Test
    void shouldOnlyCloseEventWhenHttpDeliveryWasAlreadyCompleted() {
        PaymentEventListItemDTO event = paymentEvent("EVT-HTTP-COMPLETED", 1);
        event.setPaymentSuccessHttpStage("COMPLETED");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-HTTP-COMPLETED")).thenReturn(event);
        when(paymentEventMapper.markPublishSuccess("EVT-HTTP-COMPLETED")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                " ",
                " ",
                " ",
                restTemplate
        ).republish("EVT-HTTP-COMPLETED", " payment-event-scheduler ");

        Assertions.assertTrue(success);
        verify(paymentMapper, never()).findDetail(any());
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted(any());
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted(any());
        verify(paymentEventMapper).markPublishSuccess("EVT-HTTP-COMPLETED");
    }

    @Test
    void shouldRejectMissingOrUnknownHttpStageBeforeCallingDownstream() {
        PaymentEventListItemDTO blankStage = paymentEvent("EVT-HTTP-STAGE-BLANK", 0);
        blankStage.setPaymentSuccessHttpStage(" ");
        PaymentEventListItemDTO unknownStage = paymentEvent("EVT-HTTP-STAGE-UNKNOWN", 0);
        unknownStage.setPaymentSuccessHttpStage("UNKNOWN_STAGE");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-HTTP-STAGE-BLANK"))
                .thenReturn(blankStage);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-HTTP-STAGE-UNKNOWN"))
                .thenReturn(unknownStage);

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        );

        Assertions.assertFalse(service.republish("EVT-HTTP-STAGE-BLANK", " payment-event-scheduler "));
        Assertions.assertFalse(service.republish("EVT-HTTP-STAGE-UNKNOWN", " payment-event-scheduler "));
        verify(paymentMapper, never()).findDetail(any());
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted(any());
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted(any());
        verify(paymentEventMapper).markPublishFailed("EVT-HTTP-STAGE-BLANK");
        verify(paymentEventMapper).markPublishFailed("EVT-HTTP-STAGE-UNKNOWN");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldMarkDeadLetterWhenRetryCountReachesUpperLimit() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-RETRY-003");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("ORD-001");
        event.setPublishStatus("FAILED");
        event.setPaymentSuccessHttpStage("PENDING");
        event.setRetryCount(2);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-RETRY-003")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-RETRY-003")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(response("0", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                3,
                restTemplate
        ).republish("EVT-RETRY-003", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishDeadLetter("EVT-RETRY-003");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-RETRY-003");
    }

    @Test
    void shouldKeepDeadLetterStateWhenManualRepublishFails() {
        PaymentEventListItemDTO event = paymentEvent("EVT-DEAD-LETTER-RETRY", 0);
        event.setPublishStatus("DEAD_LETTER");
        event.setPaymentSuccessHttpStage("PENDING");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-DEAD-LETTER-RETRY")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-DEAD-LETTER-RETRY")).thenReturn(event);
        when(paymentEventMapper.markPublishDeadLetter("EVT-DEAD-LETTER-RETRY")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-DEAD-LETTER-RETRY", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishDeadLetter("EVT-DEAD-LETTER-RETRY");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-DEAD-LETTER-RETRY");
    }

    @Test
    void shouldReturnFalseWhenRepublishEventMissing() {
        when(paymentEventMapper.findByEventNoForUpdate("EVT-MISSING")).thenReturn(null);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-MISSING", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findDetail(any());
    }

    @Test
    void shouldReturnFalseWhenRepublishEventTypeUnsupported() {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-REFUND-001");
        event.setEventType("REFUND_SUCCESS");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-REFUND-001")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-REFUND-001", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findDetail(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishDeadLetter(any());
    }

    @Test
    void shouldRejectRepublishingSuccessfulPaymentEventBeforeCallingDownstream() {
        PaymentEventListItemDTO event = paymentEvent("EVT-SUCCESS", 1);
        event.setPublishStatus("SUCCESS");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-SUCCESS")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-SUCCESS", " finance-ops ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findDetail(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishDeadLetter(any());
    }

    @Test
    void shouldRejectRepublishingSuccessfulRefundEventBeforeCallingChannel() {
        PaymentEventListItemDTO event = refundEvent("SUCCESS", 1);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .republish("EVTR-001", " finance-ops ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).findChannelSubmitRequestByAttemptNo(any());
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishDeadLetter(any());
    }

    @Test
    void shouldMarkFailedWhenPaymentDetailMissing() {
        PaymentEventListItemDTO event = paymentEvent("EVT-404", 0);
        event.setPaymentOrderId("PAY-404");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-404")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-404")).thenReturn(null);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-404", "PAY-404");

        verify(paymentEventMapper).markPublishFailed("EVT-404");
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldMarkDeadLetterWhenPaymentDetailRemainsMissingAtRetryLimit() {
        PaymentEventListItemDTO event = paymentEvent("EVT-404", 2);
        event.setPaymentOrderId("PAY-404");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-404")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-404")).thenReturn(null);
        when(paymentEventMapper.findByEventNo("EVT-404")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-404", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishDeadLetter("EVT-404");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-404");
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenPaymentIsNotSuccessful() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setStatus("WAIT_CALLBACK");
        PaymentEventListItemDTO event = paymentEvent("EVT-NOT-SUCCESS", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-NOT-SUCCESS")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-NOT-SUCCESS")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-NOT-SUCCESS", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-NOT-SUCCESS");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenPrepayAmountBindingIsInvalid() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setPrepayAmountBindingValid(false);
        PaymentEventListItemDTO event = paymentEvent("EVT-PREPAY-AMOUNT-INVALID", 0);
        when(paymentEventMapper.findByEventNoForUpdate(event.getEventNo())).thenReturn(event);
        when(paymentEventMapper.findByEventNo(event.getEventNo())).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).republish(event.getEventNo(), "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectStandardPaymentSuccessEventWhenBusinessOrderDisappears() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-STANDARD-SOURCE-MISSING", 0);
        when(paymentEventMapper.findByEventNoForUpdate(event.getEventNo())).thenReturn(event);
        when(paymentEventMapper.findByEventNo(event.getEventNo())).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        org.mockito.Mockito.doReturn(null).when(paymentMapper).findOrderAmount("ORD-001");
        org.mockito.Mockito.doReturn(null).when(paymentMapper).findPaidAmount("ORD-001");

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).republish(event.getEventNo(), "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectVirtualPaymentSuccessEventWhenBusinessOrderAppears() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setSourceKind("VIRTUAL");
        PaymentEventListItemDTO event = paymentEvent("EVT-VIRTUAL-SOURCE-CONFLICT", 0);
        when(paymentEventMapper.findByEventNoForUpdate(event.getEventNo())).thenReturn(event);
        when(paymentEventMapper.findByEventNo(event.getEventNo())).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).republish(event.getEventNo(), "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldDispatchVirtualPaymentSuccessWhenNoBusinessOrderExists() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setSourceKind("VIRTUAL");
        org.mockito.Mockito.doReturn(null).when(paymentMapper).findOrderAmount("ORD-001");
        org.mockito.Mockito.doReturn(null).when(paymentMapper).findPaidAmount("ORD-001");
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.markPublishSuccess("EVT-VIRTUAL-SOURCE-VALID")).thenReturn(1);
        when(restTemplate.postForEntity(any(String.class), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());

        new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).publishPaymentSuccess("EVT-VIRTUAL-SOURCE-VALID", "PAY-001");

        verify(restTemplate, org.mockito.Mockito.times(2)).postForEntity(
                any(String.class), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(paymentEventMapper).markPublishSuccess("EVT-VIRTUAL-SOURCE-VALID");
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenCreationFollowsSuccessfulAttempt() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setCreatedAt("2026-09-27 12:00:01");
        PaymentEventListItemDTO event = paymentEvent("EVT-CREATED-AFTER-ATTEMPT", 0);
        when(paymentEventMapper.findByEventNoForUpdate(event.getEventNo())).thenReturn(event);
        when(paymentEventMapper.findByEventNo(event.getEventNo())).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).republish(event.getEventNo(), "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenPaymentDetailBelongsToAnotherPayment() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setPaymentOrderId("PAY-OTHER");
        PaymentEventListItemDTO event = paymentEvent("EVT-PAYMENT-MISMATCH", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-PAYMENT-MISMATCH")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-PAYMENT-MISMATCH")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-PAYMENT-MISMATCH", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-PAYMENT-MISMATCH");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenBusinessOrderDoesNotMatchPaymentDetail() {
        PaymentEventListItemDTO event = paymentEvent("EVT-BIZ-MISMATCH", 0);
        event.setBizNo("ORD-OTHER");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-BIZ-MISMATCH")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-BIZ-MISMATCH")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-BIZ-MISMATCH", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper).markPublishFailed("EVT-BIZ-MISMATCH");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenOrderIdentityDiffersOnlyByWhitespace() {
        PaymentEventListItemDTO event = paymentEvent("EVT-ORDER-ALIAS", 0);
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setOrderNo(" ORD-001 ");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-ORDER-ALIAS")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-ORDER-ALIAS")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-ORDER-ALIAS", "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-ORDER-ALIAS");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenBothOrderIdentitiesArePadded() {
        PaymentEventListItemDTO event = paymentEvent("EVT-ORDER-PADDED", 0);
        event.setBizNo(" ORD-001 ");
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setOrderNo(" ORD-001 ");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-ORDER-PADDED")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-ORDER-PADDED")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-ORDER-PADDED", "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-ORDER-PADDED");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWithoutPaymentChannel() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setChannel(" ");
        PaymentEventListItemDTO event = paymentEvent("EVT-CHANNEL-MISSING", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-CHANNEL-MISSING")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-CHANNEL-MISSING")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-CHANNEL-MISSING", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-CHANNEL-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWithoutChannelTransactionNo() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setChannelTransactionNo(null);
        PaymentEventListItemDTO event = paymentEvent("EVT-TRACE-MISSING", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-TRACE-MISSING")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-TRACE-MISSING")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-TRACE-MISSING", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-TRACE-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenAttemptFactsAreIncomplete() {
        for (String attemptStatusType : new String[] {null, "warn"}) {
            PaymentDetailDTO detail = buildPaymentDetail();
            detail.setLatestAttemptStatusType(attemptStatusType);
            PaymentEventListItemDTO event = paymentEvent("EVT-ATTEMPT-INVALID", 0);
            when(paymentEventMapper.findByEventNoForUpdate("EVT-ATTEMPT-INVALID")).thenReturn(event);
            when(paymentEventMapper.findByEventNo("EVT-ATTEMPT-INVALID")).thenReturn(event);
            when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

            boolean success = new PaymentEventDispatchServiceImpl(
                    paymentMapper,
                    paymentEventMapper,
                    "http://127.0.0.1:18120/api/clearing/events/payments/success",
                    "http://127.0.0.1:18110/api/accounting/events/payments/success",
                    "ACT10003",
                    restTemplate
            ).republish("EVT-ATTEMPT-INVALID", " payment-event-scheduler ");

            Assertions.assertFalse(success);
        }
        verify(restTemplate, never()).postForEntity(any(), any(), eq(
                PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, org.mockito.Mockito.times(2))
                .markPublishFailed("EVT-ATTEMPT-INVALID");
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenRawTimesAreInvertedWithinSameSecond() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setPaymentFactTimelineValid(false);
        PaymentEventListItemDTO event = paymentEvent("EVT-TIMELINE-INVALID", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-TIMELINE-INVALID")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-TIMELINE-INVALID")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-TIMELINE-INVALID", "payment-event-scheduler");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-TIMELINE-INVALID");
    }

    @Test
    void shouldRejectPaymentSuccessEventWhenPaymentStatusTypeIsNotSuccess() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setStatusType("warn");
        PaymentEventListItemDTO event = paymentEvent("EVT-STATUS-TYPE-INVALID", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-STATUS-TYPE-INVALID")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-STATUS-TYPE-INVALID")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-STATUS-TYPE-INVALID", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(restTemplate, never()).postForEntity(any(), any(), eq(
                PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        verify(paymentEventMapper).markPublishFailed("EVT-STATUS-TYPE-INVALID");
    }

    @Test
    void shouldRejectInvalidPaymentSuccessPartyFieldsBeforeCallingDownstream() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-PARTY-INVALID", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentEventMapper.findByEventNo("EVT-PARTY-INVALID")).thenReturn(event);
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate);

        detail.setOrderNo("ORD-001\n");
        service.publishPaymentSuccess("EVT-PARTY-INVALID", "PAY-001");

        detail.setOrderNo("ORD-001");
        detail.setCustomerName(String.join("", Collections.nCopies(129, "C")));
        service.publishPaymentSuccess("EVT-PARTY-INVALID", "PAY-001");

        verify(paymentMapper, never()).findWorkerNameByOrderNo(any());
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper, org.mockito.Mockito.times(2)).markPublishFailed("EVT-PARTY-INVALID");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldNormalizePaymentSuccessNamesBeforeHttpDispatch() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setCustomerName(" 张女士 ");
        PaymentEventListItemDTO event = paymentEvent("EVT-PARTY-NORMALIZED", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn(" 李阿姨 ");
        when(restTemplate.postForEntity(any(String.class), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(paymentEventMapper.markPublishSuccess("EVT-PARTY-NORMALIZED")).thenReturn(1);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate).publishPaymentSuccess("EVT-PARTY-NORMALIZED", "PAY-001");

        verify(restTemplate, org.mockito.Mockito.times(2)).postForEntity(
                any(String.class), payloadCaptor.capture(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        ObjectMapper objectMapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode clearingPayload =
                objectMapper.valueToTree(payloadCaptor.getAllValues().get(0));
        com.fasterxml.jackson.databind.JsonNode accountingPayload =
                objectMapper.valueToTree(payloadCaptor.getAllValues().get(1));
        Assertions.assertEquals("ORD-001", clearingPayload.get("orderNo").asText());
        Assertions.assertEquals("张女士", clearingPayload.get("customerName").asText());
        Assertions.assertEquals("李阿姨", clearingPayload.get("workerName").asText());
        Assertions.assertEquals("ORD-001", accountingPayload.get("orderNo").asText());
        Assertions.assertEquals("张女士", accountingPayload.get("customerName").asText());
        Assertions.assertEquals("ORD-001", detail.getOrderNo());
        Assertions.assertEquals(" 张女士 ", detail.getCustomerName());
        verify(paymentEventMapper).markPublishSuccess("EVT-PARTY-NORMALIZED");
    }

    @Test
    void shouldSignExactPaymentSuccessBodyForBothHttpDownstreams() {
        String secret = "local-payment-event-test-secret";
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentEventMapper.markPublishSuccess("EVT-SIGNED-HTTP")).thenReturn(1);
        when(restTemplate.postForEntity(any(String.class), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate);
        setField(service, "paymentSuccessHttpSecret", secret);

        service.publishPaymentSuccess("EVT-SIGNED-HTTP", "PAY-001");

        ArgumentCaptor<Object> requests = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).postForEntity(any(String.class), requests.capture(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
        for (Object request : requests.getAllValues()) {
            Assertions.assertTrue(request instanceof HttpEntity);
            HttpEntity<?> signed = (HttpEntity<?>) request;
            String body = (String) signed.getBody();
            String timestamp = signed.getHeaders().getFirst(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME);
            Assertions.assertNotNull(timestamp);
            Assertions.assertTrue(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) <= 5);
            Assertions.assertEquals(PaymentEventHttpSignature.sign(body, timestamp, secret),
                    signed.getHeaders().getFirst(PaymentEventHttpSignature.HEADER_NAME));
            Assertions.assertTrue(body.contains("\"paymentOrderId\":\"PAY-001\""));
        }
        verify(paymentEventMapper).markPublishSuccess("EVT-SIGNED-HTTP");
    }

    @Test
    void shouldRejectInvalidWorkerNameBeforeCallingDownstream() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-WORKER-INVALID", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("worker\nname");
        when(paymentEventMapper.findByEventNo("EVT-WORKER-INVALID")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate).publishPaymentSuccess("EVT-WORKER-INVALID", "PAY-001");

        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-WORKER-INVALID");
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldStopAtClearingFailureWithoutPostingAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-CLEARING-FAIL");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("ORD-001");
        event.setPublishStatus("FAILED");
        event.setPaymentSuccessHttpStage("PENDING");
        event.setRetryCount(0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-CLEARING-FAIL")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(response("0", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-CLEARING-FAIL", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishFailed("EVT-CLEARING-FAIL");
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldRejectIncompleteHttpConfigurationBeforeCallingEitherDownstream() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-CONFIG-MISSING", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-CONFIG-MISSING")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                " ",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-CONFIG-MISSING", "PAY-001");

        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(paymentEventMapper).markPublishFailed("EVT-CONFIG-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-CONFIG-MISSING");
    }

    @Test
    void shouldRejectUnsupportedDispatchModeBeforeCallingEitherDownstream() {
        PaymentEventListItemDTO event = paymentEvent("EVT-MODE-INVALID", 0);
        when(paymentEventMapper.findByEventNo("EVT-MODE-INVALID")).thenReturn(event);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate,
                " amqpp ",
                rabbitTemplate,
                new ObjectMapper()
        ).publishPaymentSuccess("EVT-MODE-INVALID", "PAY-001");

        org.mockito.Mockito.verifyNoInteractions(paymentMapper);
        verify(restTemplate, never()).postForEntity(any(), any(), any());
        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-MODE-INVALID");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-MODE-INVALID");
    }

    @Test
    void shouldRejectHttpBusinessFailureWithoutCallingAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-BUSINESS-FAIL", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-BUSINESS-FAIL")).thenReturn(event);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(response("CLEARING-FAILED", HttpStatus.OK));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-BUSINESS-FAIL", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-BUSINESS-FAIL");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-BUSINESS-FAIL");
        verify(restTemplate, never()).postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldRejectClearingReceiptForAnotherPaymentBeforeCallingAccounting() {
        PaymentEventListItemDTO event = paymentEvent("EVT-WRONG-CLEARING-ACK", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-WRONG-CLEARING-ACK")).thenReturn(event);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse("PAY-OTHER"));

        new PaymentEventDispatchServiceImpl(paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).publishPaymentSuccess("EVT-WRONG-CLEARING-ACK", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-WRONG-CLEARING-ACK");
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted("EVT-WRONG-CLEARING-ACK");
        verify(restTemplate, never()).postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldRejectSuccessfulCodeWithoutStructuredClearingReceipt() {
        PaymentEventListItemDTO event = paymentEvent("EVT-MALFORMED-CLEARING-ACK", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-MALFORMED-CLEARING-ACK")).thenReturn(event);
        PaymentEventDispatchServiceImpl.DownstreamApiResponse response = successResponse().getBody();
        response.setData(new Object());
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(
                        response, HttpStatus.OK));

        new PaymentEventDispatchServiceImpl(paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).publishPaymentSuccess("EVT-MALFORMED-CLEARING-ACK", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-MALFORMED-CLEARING-ACK");
        verify(paymentEventMapper, never()).markPaymentSuccessClearingAccepted("EVT-MALFORMED-CLEARING-ACK");
    }

    @Test
    void shouldRejectAccountingReceiptForAnotherPaymentWithoutCompletingHttpStage() {
        PaymentEventListItemDTO event = paymentEvent("EVT-WRONG-ACCOUNTING-ACK", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-WRONG-ACCOUNTING-ACK")).thenReturn(event);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse("PAY-OTHER"));

        new PaymentEventDispatchServiceImpl(paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate).publishPaymentSuccess("EVT-WRONG-ACCOUNTING-ACK", "PAY-001");

        verify(paymentEventMapper).markPaymentSuccessClearingAccepted("EVT-WRONG-ACCOUNTING-ACK");
        verify(paymentEventMapper).markPublishFailed("EVT-WRONG-ACCOUNTING-ACK");
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted("EVT-WRONG-ACCOUNTING-ACK");
    }

    @Test
    void shouldKeepPaymentEventRetryableWhenReconciliationFactFailsAfterDownstreamsSucceed() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-RECONCILIATION-FAIL", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-RECONCILIATION-FAIL")).thenReturn(event);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(
                eq("http://reconciliation/facts"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(response("RECONCILIATION_UNAVAILABLE", HttpStatus.BAD_GATEWAY));

        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate);
        setField(service, "reconciliationFactUrl", "http://reconciliation/facts");

        service.publishPaymentSuccess("EVT-RECONCILIATION-FAIL", "PAY-001");
        verify(paymentEventMapper).markPublishFailed("EVT-RECONCILIATION-FAIL");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-RECONCILIATION-FAIL");
        verify(restTemplate).postForEntity(
                eq("http://reconciliation/facts"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldCompletePaymentEventWhenReconciliationReceiptContainsItsFact() {
        PaymentEventDispatchServiceImpl service = reconciliationDispatch();
        when(paymentEventMapper.markPublishSuccess("EVT-RECONCILIATION-ACK")).thenReturn(1);
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(reconciliationResponse("PAY-001"));

        service.publishPaymentSuccess("EVT-RECONCILIATION-ACK", "PAY-001");

        verify(paymentEventMapper).markPaymentSuccessHttpCompleted("EVT-RECONCILIATION-ACK");
        verify(paymentEventMapper).markPublishSuccess("EVT-RECONCILIATION-ACK");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-RECONCILIATION-ACK");
    }

    @Test
    void shouldRejectReconciliationReceiptForAnotherBusinessChain() {
        PaymentEventDispatchServiceImpl service = reconciliationDispatch();
        when(paymentEventMapper.findByEventNo("EVT-RECONCILIATION-CHAIN"))
                .thenReturn(paymentEvent("EVT-RECONCILIATION-CHAIN", 0));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(reconciliationResponse("PAY-OTHER"));

        service.publishPaymentSuccess("EVT-RECONCILIATION-CHAIN", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-RECONCILIATION-CHAIN");
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted("EVT-RECONCILIATION-CHAIN");
    }

    @Test
    void shouldRejectReconciliationReceiptWithoutMatchingPaymentFact() {
        PaymentEventDispatchServiceImpl service = reconciliationDispatch();
        when(paymentEventMapper.findByEventNo("EVT-RECONCILIATION-FACT"))
                .thenReturn(paymentEvent("EVT-RECONCILIATION-FACT", 0));
        PaymentEventDispatchServiceImpl.DownstreamApiResponse response = reconciliationResponse("PAY-001").getBody();
        Map<?, ?> snapshot = (Map<?, ?>) response.getData();
        Map<String, Object> fact = (Map<String, Object>) ((java.util.List<?>) snapshot.get("facts")).get(0);
        fact.put("amount", new BigDecimal("169.00"));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(
                        response, HttpStatus.OK));

        service.publishPaymentSuccess("EVT-RECONCILIATION-FACT", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-RECONCILIATION-FACT");
        verify(paymentEventMapper, never()).markPaymentSuccessHttpCompleted("EVT-RECONCILIATION-FACT");
    }

    private PaymentEventDispatchServiceImpl reconciliationDispatch() {
        when(paymentMapper.findDetail("PAY-001")).thenReturn(buildPaymentDetail());
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentMapper, paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003", restTemplate);
        setField(service, "reconciliationFactUrl", "http://reconciliation/facts");
        return service;
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> reconciliationResponse(
            String paymentOrderId) {
        Map<String, Object> fact = new LinkedHashMap<String, Object>();
        fact.put("businessChainId", "CHAIN-" + paymentOrderId);
        fact.put("systemCode", "payment-core");
        fact.put("factType", "PAYMENT_SUCCESS");
        fact.put("factNo", paymentOrderId);
        fact.put("businessNo", "ORD-001");
        fact.put("payScene", "HOME_CLEAN");
        fact.put("factStatus", "SUCCESS");
        fact.put("amount", new BigDecimal("168.00"));
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("businessChainId", "CHAIN-" + paymentOrderId);
        snapshot.put("facts", Collections.singletonList(fact));
        PaymentEventDispatchServiceImpl.DownstreamApiResponse response =
                new PaymentEventDispatchServiceImpl.DownstreamApiResponse();
        response.setCode("0");
        response.setData(snapshot);
        return new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(response, HttpStatus.OK);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法设置测试字段 " + fieldName, exception);
        }
    }

    private Object getField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法读取测试字段 " + fieldName, exception);
        }
    }

    @Test
    void shouldFailWhenPaymentSuccessEventStatusCannotBeUpdated() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-STATUS-CONFLICT", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-STATUS-CONFLICT")).thenReturn(event);
        when(paymentEventMapper.findByEventNo("EVT-STATUS-CONFLICT")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(paymentEventMapper.markPublishSuccess("EVT-STATUS-CONFLICT")).thenReturn(0);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-STATUS-CONFLICT", " payment-event-scheduler ");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishFailed("EVT-STATUS-CONFLICT");
    }

    @Test
    void shouldFailWhenFailureStateCannotBeUpdated() {
        PaymentEventListItemDTO event = paymentEvent("EVT-FAILURE-STATE", 0);
        when(paymentEventMapper.findByEventNoForUpdate("EVT-FAILURE-STATE")).thenReturn(
                refundEvent("PENDING", 0));
        when(paymentEventMapper.findByEventNo("EVT-FAILURE-STATE")).thenReturn(event);
        when(paymentEventMapper.markPublishFailed("EVT-FAILURE-STATE")).thenReturn(0);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PaymentEventDispatchServiceImpl(
                        paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                        .publishRefundSubmit("EVT-FAILURE-STATE", "RFA-001", "operator"));
        verify(paymentEventMapper).markPublishFailed("EVT-FAILURE-STATE");
    }

    @Test
    void shouldRejectEmptyHttpSuccessResponse() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-EMPTY-RESPONSE", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNo("EVT-EMPTY-RESPONSE")).thenReturn(event);
        when(restTemplate.postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                any(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(
                        (PaymentEventDispatchServiceImpl.DownstreamApiResponse) null,
                        HttpStatus.OK));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-EMPTY-RESPONSE", "PAY-001");

        verify(paymentEventMapper).markPublishFailed("EVT-EMPTY-RESPONSE");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-EMPTY-RESPONSE");
    }

    @Test
    void shouldBuildExpectedPayloadForClearingAndAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                .thenReturn(successResponse());

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        ArgumentCaptor<Object> clearingCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> accountingCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                clearingCaptor.capture(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)
        );
        verify(restTemplate).postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                accountingCaptor.capture(),
                eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)
        );
        Assertions.assertEquals("PAY-001", invokeGetter(clearingCaptor.getValue(), "getPaymentOrderId"));
        Assertions.assertEquals("李阿姨", invokeGetter(clearingCaptor.getValue(), "getWorkerName"));
        Assertions.assertEquals("ACT10003", invokeGetter(accountingCaptor.getValue(), "getAccountNo"));
        Assertions.assertEquals("PAY-001", invokeGetter(accountingCaptor.getValue(), "getPaymentOrderId"));
    }

    @Test
    void shouldBuildClearingBatchDateInBusinessTimezone() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate businessToday = LocalDate.now(businessZone);
        ZoneId alternativeZone = ZoneId.of("Pacific/Kiritimati");
        if (businessToday.equals(LocalDate.now(alternativeZone))) {
            alternativeZone = ZoneId.of("Etc/GMT+12");
        }
        Assertions.assertNotEquals(businessToday, LocalDate.now(alternativeZone));

        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(alternativeZone));
            PaymentDetailDTO detail = buildPaymentDetail();
            when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
            when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
            when(paymentEventMapper.markPublishSuccess("EVT-BUSINESS-DATE")).thenReturn(1);
            when(restTemplate.postForEntity(any(String.class), any(),
                    eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class)))
                    .thenReturn(successResponse());
            ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

            new PaymentEventDispatchServiceImpl(
                    paymentMapper,
                    paymentEventMapper,
                    "http://127.0.0.1:18120/api/clearing/events/payments/success",
                    "http://127.0.0.1:18110/api/accounting/events/payments/success",
                    "ACT10003",
                    restTemplate
            ).publishPaymentSuccess("EVT-BUSINESS-DATE", "PAY-001");

            verify(restTemplate).postForEntity(
                    eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                    payloadCaptor.capture(),
                    eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
            Assertions.assertEquals(
                    businessToday.toString(), invokeGetter(payloadCaptor.getValue(), "getBatchDate"));
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    private PaymentDetailDTO buildPaymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-001");
        detail.setOrderNo("ORD-001");
        detail.setCustomerName("张女士");
        detail.setAmount("¥168.00");
        detail.setStatus("SUCCESS");
        detail.setStatusType("success");
        detail.setPrepayAmountBindingValid(true);
        detail.setSourceKind("STANDARD_ORDER");
        detail.setCreatedAt("2026-09-27 11:59:00");
        detail.setChannel("wx_h5");
        detail.setPayScene("HOME_CLEAN");
        detail.setChannelTransactionNo("WX-001");
        detail.setLatestAttemptStatus("成功");
        detail.setLatestAttemptStatusType("success");
        detail.setLatestAttemptCompletedAt("2026-09-27 12:00:00");
        detail.setSuccessAt("2026-09-27 12:01:00");
        detail.setPaymentFactTimelineValid(true);
        return detail;
    }

    private PaymentEventListItemDTO paymentEvent(String eventNo, int retryCount) {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo(eventNo);
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("ORD-001");
        event.setPublishStatus("FAILED");
        event.setPaymentSuccessHttpStage("PENDING");
        event.setRetryCount(retryCount);
        return event;
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> successResponse() {
        return response("0", HttpStatus.OK);
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> successResponse(String paymentOrderId) {
        return response("0", HttpStatus.OK, paymentOrderId);
    }

    private String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> response(
            String code,
            HttpStatus status) {
        return response(code, status, "PAY-001");
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> response(
            String code,
            HttpStatus status,
            String paymentOrderId) {
        PaymentEventDispatchServiceImpl.DownstreamApiResponse body =
                new PaymentEventDispatchServiceImpl.DownstreamApiResponse();
        body.setCode(code);
        Map<String, String> event = new LinkedHashMap<String, String>();
        event.put("eventNo", "ACK-001");
        event.put("eventType", "PAYMENT_SUCCESS");
        event.put("bizNo", paymentOrderId);
        event.put("businessChainId", "CHAIN-" + paymentOrderId);
        event.put("eventStatus", "已消费");
        body.setData(event);
        return new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(body, status);
    }

    private PaymentEventListItemDTO refundEvent(String publishStatus, int retryCount) {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVTR-001");
        event.setEventType("REFUND_SUBMIT");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("RFA-001");
        event.setDownstreamSystem("gateway-access");
        event.setEventTopic("payment.refund.submitted.v1");
        event.setPublishStatus(publishStatus);
        event.setRetryCount(retryCount);
        return event;
    }

    private RefundChannelSubmitRequestDTO refundSubmitRequest() {
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setRefundAttemptNo("RFA-001");
        request.setPaymentOrderId("PAY-001");
        request.setChannelCode("wx_h5");
        request.setRefundAmount(new java.math.BigDecimal("20.00"));
        request.setRefundStatus("PROCESSING");
        request.setAttemptStatus("PENDING");
        return request;
    }

    private Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
