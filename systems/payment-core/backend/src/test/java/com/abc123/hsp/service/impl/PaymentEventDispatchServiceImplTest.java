package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
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

    @Test
    void shouldDispatchRefundInNewTransactionAfterApprovalCommit() throws Exception {
        Method method = PaymentEventDispatchServiceImpl.class.getMethod(
                "publishRefundSubmit", String.class, String.class, String.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        Assertions.assertNotNull(transactional);
        Assertions.assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());
    }

    @Test
    void shouldDispatchRefundSubmitAndPersistAcceptedFact() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-001");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.bindRefundChannelAccepted("REF-001", "wx_h5", "RCH-REF-001")).thenReturn(1);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService).submit(request);
        verify(refundMapper).bindRefundChannelAccepted("REF-001", "wx_h5", "RCH-REF-001");
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT".equals(log.getActionCode())
                        && "refund-operator".equals(log.getOperatorName())
                        && log.getOperationRemark().contains("RCH-REF-001")));
    }

    @Test
    void shouldRepublishFailedRefundAndKeepInitiatorInSubmitLog() {
        PaymentEventListItemDTO event = refundEvent("FAILED", 1);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-RETRY-001");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);
        when(refundMapper.bindRefundChannelAccepted(
                "REF-001", "wx_h5", "RCH-REF-RETRY-001")).thenReturn(1);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .republish(" EVTR-001 ", " finance-ops ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService).submit(request);
        verify(refundMapper).bindRefundChannelAccepted(
                "REF-001", "wx_h5", "RCH-REF-RETRY-001");
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT".equals(log.getActionCode())
                        && "finance-ops".equals(log.getOperatorName())
                        && log.getOperationRemark().contains("RCH-REF-RETRY-001")));
    }

    @Test
    void shouldKeepRefundProcessingAndStopRetryWhenChannelResultIsUnknown() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("timeout", true));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper).recordUnknownRefundSubmitChannel("REF-001", "wx_h5");
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
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request))
                .thenThrow(new RefundChannelSubmitException("not configured", false));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).recordUnknownRefundSubmitChannel(any(), any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(paymentEventMapper, never()).markPublishUnknown("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT_FAILED".equals(log.getActionCode())
                        && "refund-operator".equals(log.getOperatorName())));

    }

    @Test
    void shouldRejectInvalidDispatchOperatorBeforeReadingEvent() {
        PaymentEventDispatchServiceImpl service = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.publishRefundSubmit("EVTR-001", "REF-001", " "));
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
        IllegalArgumentException controlRefundOrderId = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.publishRefundSubmit("EVTR-001", "REF-001\n", "finance-ops"));

        Assertions.assertEquals("事件号不能为空", blankEventNo.getMessage());
        Assertions.assertEquals("事件号长度不能超过64字符", oversizedEventNo.getMessage());
        Assertions.assertEquals("事件号不能包含控制字符", controlEventNo.getMessage());
        Assertions.assertEquals("支付单号长度不能超过64字符", oversizedPaymentOrderId.getMessage());
        Assertions.assertEquals("退款单号不能包含控制字符", controlRefundOrderId.getMessage());
        org.mockito.Mockito.verifyNoInteractions(
                paymentMapper, paymentEventMapper, refundMapper, refundChannelSubmitService);
    }

    @Test
    void shouldRejectInconsistentRefundSubmitSnapshotBeforeCallingChannel() {
        PaymentEventListItemDTO event = refundEvent("PENDING", 0);
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setPaymentOrderId("PAY-OTHER");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishFailed("EVTR-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_SUBMIT_FAILED".equals(log.getActionCode())));
    }

    @Test
    void shouldNotSubmitRefundAgainWhenEventAlreadySucceeded() {
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("SUCCESS", 1));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
    }

    @Test
    void shouldRejectDirectRepublishWhenRefundResultIsUnknown() {
        PaymentEventListItemDTO event = refundEvent("UNKNOWN", 1);
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001"))
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
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001")).thenReturn(event);
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishUnknown(any());
    }

    @Test
    void shouldCompleteEventWithoutCallingChannelWhenAcceptedTraceAlreadyExists() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        request.setSubmittedChannelCode("WX_H5");
        request.setChannelRefundNo("RCH-REF-001");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("FAILED", 1));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(paymentEventMapper.markPublishSuccess("EVTR-001")).thenReturn(1);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertTrue(success);
        verify(refundChannelSubmitService, never()).submit(any());
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any());
        verify(paymentEventMapper).markPublishSuccess("EVTR-001");
    }

    @Test
    void shouldRejectOversizedChannelRefundNoBeforePersistingAcceptedTrace() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo(new String(new char[129]).replace('\0', 'R'));
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("PENDING", 0));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any());
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldRejectChannelRefundNoControlCharacterBeforePersistingAcceptedTrace() {
        RefundChannelSubmitRequestDTO request = refundSubmitRequest();
        RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
        result.setChannelRefundNo("RCH-REF-001\n");
        when(paymentEventMapper.findByEventNoForUpdate("EVTR-001"))
                .thenReturn(refundEvent("PENDING", 0));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-001")).thenReturn(request);
        when(refundChannelSubmitService.submit(request)).thenReturn(result);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentEventMapper, refundMapper, refundChannelSubmitService, 3)
                .publishRefundSubmit("EVTR-001", "REF-001", " refund-operator ");

        Assertions.assertFalse(success);
        verify(refundMapper, never()).bindRefundChannelAccepted(any(), any(), any());
        verify(paymentEventMapper).markPublishUnknown("EVTR-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVTR-001");
    }

    @Test
    void shouldPublishPaymentSuccessToAmqpWithTraceableMessageProperties() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1"
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> processorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                eq("payment.trade"),
                eq("payment.success.v1"),
                payloadCaptor.capture(),
                processorCaptor.capture()
        );
        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"paymentOrderId\":\"PAY-001\""));
        Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"accountNo\":\"ACT10003\""));

        Message message = processorCaptor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        Assertions.assertEquals("EVT-001", message.getMessageProperties().getMessageId());
        Assertions.assertEquals("EVT-001", message.getMessageProperties().getCorrelationId());
        Assertions.assertEquals("PAYMENT_SUCCESS", message.getMessageProperties().getHeaders().get("eventType"));
        Assertions.assertEquals("PAY-001", message.getMessageProperties().getHeaders().get("paymentOrderId"));
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
        verify(restTemplate, never()).postForEntity(
                any(), any(), eq(PaymentEventDispatchServiceImpl.DownstreamApiResponse.class));
    }

    @Test
    void shouldCapPaymentSuccessAmqpPublisherConfirmTimeout() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1",
                120000L
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        verify(rabbitTemplate).waitForConfirmsOrDie(60000L);
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldUseDefaultPaymentSuccessAmqpPublisherConfirmTimeoutWhenNonPositive() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.markPublishSuccess("EVT-001")).thenReturn(1);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1",
                0L
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        verify(rabbitTemplate).waitForConfirmsOrDie(5000L);
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
    }

    @Test
    void shouldMarkFailedWhenAmqpPublishFails() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setPublishStatus("FAILED");
        event.setRetryCount(0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(paymentEventMapper.findByEventNoForUpdate("EVT-001")).thenReturn(event);
        when(rabbitTemplate.invoke(any())).thenAnswer(invocation -> {
            org.springframework.amqp.rabbit.core.RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(rabbitTemplate);
        });
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate).convertAndSend(eq("payment.trade"), eq("payment.success.v1"), any(), any(MessagePostProcessor.class));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "ACT10003",
                3,
                rabbitTemplate,
                new ObjectMapper(),
                "payment.trade",
                "payment.success.v1"
        ).republish("EVT-001", " payment-event-scheduler ");

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

        verify(rabbitTemplate, never()).invoke(any());
        verify(paymentEventMapper).markPublishFailed("EVT-AMQP-CONFIG-MISSING");
        verify(paymentEventMapper, never()).markPublishSuccess("EVT-AMQP-CONFIG-MISSING");
    }

    @Test
    void shouldPublishPaymentSuccessToClearingAndAccounting() {
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
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-001");
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
        event.setPublishStatus("FAILED");
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
        verify(paymentEventMapper).markPublishFailed("EVT-001");
    }

    @Test
    void shouldMarkDeadLetterWhenRetryCountReachesUpperLimit() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-RETRY-003");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setPublishStatus("FAILED");
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
        verify(refundMapper, never()).findChannelSubmitRequestByRefundOrderId(any());
        verify(refundChannelSubmitService, never()).submit(any());
        verify(paymentEventMapper, never()).markPublishSuccess(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishDeadLetter(any());
    }

    @Test
    void shouldMarkFailedWhenPaymentDetailMissing() {
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
    void shouldNormalizePaymentSuccessPartyFieldsBeforeHttpDispatch() {
        PaymentDetailDTO detail = buildPaymentDetail();
        detail.setOrderNo(" ORD-001 ");
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
        Assertions.assertEquals(" ORD-001 ", detail.getOrderNo());
        Assertions.assertEquals(" 张女士 ", detail.getCustomerName());
        verify(paymentEventMapper).markPublishSuccess("EVT-PARTY-NORMALIZED");
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
        event.setPublishStatus("FAILED");
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
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = paymentEvent("EVT-MODE-INVALID", 0);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
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
        detail.setChannel("wx_h5");
        detail.setChannelTransactionNo("WX-001");
        return detail;
    }

    private PaymentEventListItemDTO paymentEvent(String eventNo, int retryCount) {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo(eventNo);
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setPublishStatus("FAILED");
        event.setRetryCount(retryCount);
        return event;
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> successResponse() {
        return response("0", HttpStatus.OK);
    }

    private ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse> response(
            String code,
            HttpStatus status) {
        PaymentEventDispatchServiceImpl.DownstreamApiResponse body =
                new PaymentEventDispatchServiceImpl.DownstreamApiResponse();
        body.setCode(code);
        body.setData(new Object());
        return new ResponseEntity<PaymentEventDispatchServiceImpl.DownstreamApiResponse>(body, status);
    }

    private PaymentEventListItemDTO refundEvent(String publishStatus, int retryCount) {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVTR-001");
        event.setEventType("REFUND_SUBMIT");
        event.setPaymentOrderId("PAY-001");
        event.setBizNo("REF-001");
        event.setPublishStatus(publishStatus);
        event.setRetryCount(retryCount);
        return event;
    }

    private RefundChannelSubmitRequestDTO refundSubmitRequest() {
        RefundChannelSubmitRequestDTO request = new RefundChannelSubmitRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setPaymentOrderId("PAY-001");
        request.setChannelCode("wx_h5");
        request.setRefundAmount(new java.math.BigDecimal("20.00"));
        request.setRefundStatus("PROCESSING");
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
