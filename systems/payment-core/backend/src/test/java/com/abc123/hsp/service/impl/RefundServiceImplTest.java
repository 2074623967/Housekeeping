package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundOperationLogItemDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.dto.RefundPaymentSourceDTO;
import com.abc123.hsp.dto.RefundRetrySourceDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundChannelQueryRateLimitService;
import com.abc123.hsp.service.RefundChannelQueryService;
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
    private PaymentEventDispatchService paymentEventDispatchService;

    @Mock
    private PaymentCallbackSignatureService paymentCallbackSignatureService;

    @Mock
    private RefundChannelQueryService refundChannelQueryService;

    @Mock
    private RefundChannelQueryRateLimitService refundChannelQueryRateLimitService;

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
        createService().list(query);

        assertEquals(1, query.getPageNo());
        assertEquals(100, query.getPageSize());
        verify(refundMapper).findAll(query);
        verify(refundMapper).count(query);
    }

    @Test
    void shouldMaskRefundListWithoutChangingMapperDto() {
        RefundQueryDTO query = new RefundQueryDTO();
        RefundListItemDTO item = new RefundListItemDTO();
        item.setCustomerName("张女士");
        when(refundMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        when(refundMapper.count(query)).thenReturn(1L);

        PageResultDTO<RefundListItemDTO> result =
                createService().list(query);

        assertEquals("张*", result.getItems().get(0).getCustomerName());
        assertEquals("张女士", item.getCustomerName());
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

        String csv = createService().exportCsv(query);

        assertEquals("REF-001", query.getRefundOrderId());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("SUCCESS", query.getRefundStatus());
        assertEquals("原路退款", query.getRefundMethod());
        assertTrue(csv.startsWith("\uFEFF退款单号,支付单号,订单号,客户名称"));
        assertTrue(csv.contains("\"success\""));
        assertTrue(csv.contains("\"张*\""));
        assertFalse(csv.contains("张\"女士"));
        assertEquals("张\"女士", item.getCustomerName());
        verify(refundMapper).findAllForExport(query);
    }

    @Test
    void shouldApplyRefundForSuccessfulPayment() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setRefundAmount(new BigDecimal("20.00"));
        request.setRefundMethod("原路退款");
        request.setOperator(" refund-applicant ");

        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setOrderNo("ORD-001");
        source.setCustomerName("张女士");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(new BigDecimal("30.00"));

        createService().apply(request);

        verify(refundMapper).insertRefund(
                org.mockito.ArgumentMatchers.startsWith("REF"),
                org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq("REVIEWING"),
                org.mockito.ArgumentMatchers.eq("warn")
        );
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "APPLY".equals(log.getActionCode())
                        && "refund-applicant".equals(log.getOperatorName())));
    }

    @Test
    void shouldRejectRefundWhenAmountExceedsPaidAmount() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setRefundAmount(new BigDecimal("80.00"));
        request.setRefundMethod("原路退款");
        request.setOperator("refund-applicant");

        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(new BigDecimal("30.00"));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> createService().apply(request)
        );
    }

    @Test
    void shouldApproveReviewingRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setOperator(" refund-approver ");
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
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            createService().approve(request);
            verify(paymentEventDispatchService, org.mockito.Mockito.never()).publishRefundSubmit(
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString(),
                    org.mockito.ArgumentMatchers.anyString());
            java.util.List<org.springframework.transaction.support.TransactionSynchronization> synchronizations =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        verify(refundMapper).upsertRefundSubmitEvent(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq("REF-001"),
                org.mockito.ArgumentMatchers.eq("PAY-001"));
        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq("REF-001"),
                org.mockito.ArgumentMatchers.eq("refund-approver"));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "APPROVE".equals(log.getActionCode())
                        && "refund-approver".equals(log.getOperatorName())));
        verify(refundMapper).findByRefundOrderId("REF-001");
    }

    @Test
    void shouldNotLogRefundDispatchFailureDetailsAfterCommit() {
        String sensitiveMessage = "jdbc:mysql://internal/payment password=refund-dispatch-secret\ninvalid SQL";
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-DISPATCH-FAIL");
        request.setOperator("refund-approver");
        when(refundMapper.updateRefundStatus(
                "REF-DISPATCH-FAIL", "REVIEWING", "PROCESSING", "warn", false)).thenReturn(1);
        RefundChannelSubmitRequestDTO submitRequest = new RefundChannelSubmitRequestDTO();
        submitRequest.setRefundOrderId("REF-DISPATCH-FAIL");
        submitRequest.setPaymentOrderId("PAY-DISPATCH-FAIL");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setRefundAmount(new BigDecimal("20.00"));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-DISPATCH-FAIL"))
                .thenReturn(submitRequest);
        org.mockito.Mockito.doThrow(new IllegalStateException(sensitiveMessage))
                .when(paymentEventDispatchService).publishRefundSubmit(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("REF-DISPATCH-FAIL"),
                        org.mockito.ArgumentMatchers.eq("refund-approver"));
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(RefundServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            createService().approve(request);
            java.util.List<org.springframework.transaction.support.TransactionSynchronization> synchronizations =
                    org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, synchronizations.size());
            synchronizations.get(0).afterCommit();

            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.get(0);
            assertTrue(event.getFormattedMessage().contains(IllegalStateException.class.getName()));
            assertFalse(event.getFormattedMessage().contains("refund-dispatch-secret"));
            assertFalse(event.getFormattedMessage().contains("REF-DISPATCH-FAIL"));
            org.junit.jupiter.api.Assertions.assertNull(event.getThrowableProxy());
            verify(refundMapper).upsertRefundSubmitEvent(
                    org.mockito.ArgumentMatchers.startsWith("EVTR"),
                    org.mockito.ArgumentMatchers.eq("REF-DISPATCH-FAIL"),
                    org.mockito.ArgumentMatchers.eq("PAY-DISPATCH-FAIL"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void shouldResubmitChannelWhenRetryFailedRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-002");
        request.setOperator(" refund-retry-operator ");
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
        stubRetryCapacity("REF-002", "PAY-002", new BigDecimal("80.00"),
                new BigDecimal("20.00"), new BigDecimal("100.00"));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-002")).thenReturn(submitRequest);
        createService().retry(request);

        verify(refundMapper).upsertRefundSubmitEvent(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq("REF-002"),
                org.mockito.ArgumentMatchers.eq("PAY-002"));
        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq("REF-002"),
                org.mockito.ArgumentMatchers.eq("refund-retry-operator"));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "RETRY".equals(log.getActionCode())
                        && "refund-retry-operator".equals(log.getOperatorName())));
        verify(refundMapper).findByRefundOrderId("REF-002");
    }

    @Test
    void shouldResubmitChannelForTaskRetryAndKeepTriggerIdentity() {
        when(refundMapper.updateRefundStatus("REF-TASK", "FAIL", "PROCESSING", "warn", false)).thenReturn(1);
        RefundChannelSubmitRequestDTO submitRequest = new RefundChannelSubmitRequestDTO();
        submitRequest.setRefundOrderId("REF-TASK");
        submitRequest.setPaymentOrderId("PAY-TASK");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setRefundAmount(new BigDecimal("20.00"));
        stubRetryCapacity("REF-TASK", "PAY-TASK", new BigDecimal("20.00"),
                BigDecimal.ZERO, new BigDecimal("100.00"));
        when(refundMapper.findChannelSubmitRequestByRefundOrderId("REF-TASK")).thenReturn(submitRequest);
        createService()
                .retryForTask("REF-TASK", "refund-retry-scheduler");

        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq("REF-TASK"),
                org.mockito.ArgumentMatchers.eq("refund-retry-scheduler"));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "TASK_RETRY".equals(log.getActionCode())
                        && "refund-retry-scheduler".equals(log.getOperatorName())
                        && "FAIL".equals(log.getFromStatus())
                        && "PROCESSING".equals(log.getToStatus())));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectFailedRefundRetryWhenReplacementConsumesCapacity() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-FAILED");
        request.setOperator("refund-retry-operator");
        stubRetryCapacity("REF-FAILED", "PAY-001", new BigDecimal("60.00"),
                new BigDecimal("60.00"), new BigDecimal("100.00"));

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> createService().retry(request));

        assertEquals("累计退款金额不能超过原支付金额", exception.getMessage());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verifyNoInteractions(paymentEventDispatchService);
    }

    @Test
    void shouldRejectFailedRefundRetryWhenCapacitySnapshotIsInvalid() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-INVALID");
        request.setOperator("refund-retry-operator");
        RefundRetrySourceDTO retrySource = new RefundRetrySourceDTO();
        retrySource.setRefundOrderId("REF-INVALID");
        retrySource.setPaymentOrderId("PAY-001");
        retrySource.setStatus("FAIL");
        when(refundMapper.findRetrySourceForUpdate("REF-INVALID")).thenReturn(retrySource);

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> createService().retry(request));

        assertEquals("退款额度数据异常", exception.getMessage());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verifyNoInteractions(paymentEventDispatchService);
    }

    @Test
    void shouldLoadRefundDetailWithOperationLogs() {
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId("REF-001");
        detail.setCustomerName("张女士");
        RefundOperationLogItemDTO logItem = new RefundOperationLogItemDTO();
        logItem.setLogNo("ROL-001");
        when(refundMapper.findDetailByRefundOrderId("REF-001")).thenReturn(detail);
        when(refundMapper.findOperationLogs("REF-001")).thenReturn(Arrays.asList(logItem));

        RefundDetailDTO result = createService().detail("REF-001");

        org.junit.jupiter.api.Assertions.assertEquals("REF-001", result.getRefundOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("张*", result.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getOperationLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals("张女士", detail.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals(null, detail.getOperationLogs());
        verify(refundMapper).findDetailByRefundOrderId("REF-001");
        verify(refundMapper).findOperationLogs("REF-001");
    }

    @Test
    void shouldFinalizeTrustedSuccessfulRefundCallback() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        mapperItem.setCustomerName("张女士");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "SUCCESS", "success", true))
                .thenReturn(1);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        RefundListItemDTO result = new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService)
                .callback(" WX_H5 ", request);

        assertEquals("张*", result.getCustomerName());
        assertEquals("SUCCESS", request.getRefundStatus());
        verify(paymentCallbackSignatureService).verifyRefund("wx_h5", request);
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "SUCCESS".equals(log.getActionCode())
                        && "channel-callback".equals(log.getOperatorName())));
    }

    @Test
    void shouldReturnIdempotentlyForRepeatedSameFinalRefundStatus() {
        RefundCallbackRequestDTO request = buildCallbackRequest("success");
        RefundCallbackSourceDTO source = buildCallbackSource("SUCCESS", "WX_H5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        new RefundServiceImpl(refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService)
                .callback("wx_h5", request);

        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .insertOperationLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectRefundCallbackChannelAndTransactionMismatch() {
        RefundCallbackRequestDTO request = buildCallbackRequest("FAIL");
        when(refundMapper.findCallbackSourceForUpdate("REF-001"))
                .thenReturn(buildCallbackSource("PROCESSING", "alipay_h5", "CHANNEL-REF-001"));
        RefundServiceImpl service = new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService);

        BusinessException channelException = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.callback("wx_h5", request));
        assertEquals(ErrorCode.REFUND_CALLBACK_CHANNEL_MISMATCH, channelException.getCode());

        when(refundMapper.findCallbackSourceForUpdate("REF-001"))
                .thenReturn(buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-OTHER"));
        BusinessException transactionException = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.callback("wx_h5", request));
        assertEquals(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH, transactionException.getCode());
    }

    @Test
    void shouldRecoverUnknownRefundAcceptanceFromVerifiedCallback() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        source.setSubmitEventStatus("UNKNOWN");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.bindUnknownRefundChannelAccepted(
                "REF-001", "wx_h5", "CHANNEL-REF-001")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("REF-001")).thenReturn(1);
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "SUCCESS", "success", true))
                .thenReturn(1);

        new RefundServiceImpl(refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService).callback("wx_h5", request);

        verify(paymentCallbackSignatureService).verifyRefund("wx_h5", request);
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_ACCEPTANCE_RECOVERED".equals(log.getActionCode())
                        && "channel-callback".equals(log.getOperatorName())));
        verify(refundMapper).markUnknownRefundSubmitEventConfirmed("REF-001");
    }

    @Test
    void shouldRejectConflictingOrUnsupportedRefundCallbackStatus() {
        RefundCallbackRequestDTO request = buildCallbackRequest("FAIL");
        when(refundMapper.findCallbackSourceForUpdate("REF-001"))
                .thenReturn(buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001"));
        RefundServiceImpl service = new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService);

        BusinessException conflictException = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.callback("wx_h5", request));
        assertEquals(ErrorCode.REFUND_CALLBACK_FINAL_STATUS_CONFLICT, conflictException.getCode());

        request.setRefundStatus("PENDING");
        BusinessException statusException = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> service.callback("wx_h5", request));
        assertEquals(ErrorCode.REFUND_CALLBACK_STATUS_INVALID, statusException.getCode());
    }

    @Test
    void shouldRecordProcessingRefundChannelQueryWithoutChangingStatus() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenReturn(buildQueryResult("PROCESSING", "CHANNEL-REF-001"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        RefundListItemDTO result = createService().query(queryRequest());

        assertEquals("REF-001", result.getRefundOrderId());
        verify(refundChannelQueryRateLimitService).acquire("wx_h5", "REF-001");
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && "PROCESSING".equals(log.getToStatus())));
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldFinalizeRefundFromTrustedChannelQuery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenReturn(buildQueryResult(" success ", " CHANNEL-REF-001 "));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "SUCCESS", "success", true))
                .thenReturn(1);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        createService().query(queryRequest());

        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_SUCCESS".equals(log.getActionCode())
                        && "SUCCESS".equals(log.getToStatus())
                        && "refund-query-operator".equals(log.getOperatorName())));
    }

    @Test
    void shouldRecoverUnknownRefundAcceptanceFromTrustedChannelQuery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        source.setSubmitEventStatus("UNKNOWN");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        mapperItem.setStatus("PROCESSING");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source))
                .thenReturn(buildQueryResult("PROCESSING", "CHANNEL-REF-RECOVERED"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.bindUnknownRefundChannelAccepted(
                "REF-001", "wx_h5", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("REF-001")).thenReturn(1);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        RefundListItemDTO result = createService().query(queryRequest());

        assertEquals("PROCESSING", result.getStatus());
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_ACCEPTANCE_RECOVERED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && !log.getOperationRemark().contains("CHANNEL-REF-RECOVERED")));
        verify(refundMapper).markUnknownRefundSubmitEventConfirmed("REF-001");
    }

    @Test
    void shouldForceRollbackWhenFinalStatusUpdateFailsAfterUnknownAcceptanceRecovery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        source.setSubmitEventStatus("UNKNOWN");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source))
                .thenReturn(buildQueryResult("SUCCESS", "CHANNEL-REF-RECOVERED"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.bindUnknownRefundChannelAccepted(
                "REF-001", "wx_h5", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("REF-001")).thenReturn(1);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> createService().query(queryRequest()));

        assertEquals("refund status update failed after unknown acceptance recovery",
                exception.getMessage());
    }

    @Test
    void shouldRejectRefundWithoutAcceptedTraceOrUnknownSubmitBeforeChannelQuery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryRateLimitService);
    }

    @Test
    void shouldFinalizeFailedRefundFromTrustedChannelQuery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenReturn(buildQueryResult("FAIL", "CHANNEL-REF-001"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "FAIL", "danger", false))
                .thenReturn(1);

        createService().query(queryRequest());

        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_FAIL".equals(log.getActionCode())
                        && "FAIL".equals(log.getToStatus())
                        && "refund-query-operator".equals(log.getOperatorName())));
    }

    @Test
    void shouldRejectMismatchedRefundTransactionFromChannelQuery() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenReturn(buildQueryResult("SUCCESS", "CHANNEL-REF-OTHER"));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_FAILED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && "退款渠道查单未获得可信结果".equals(log.getOperationRemark())));
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .findCallbackSourceForUpdate(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRecordMinimalLogWhenRefundChannelQueryFails() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenThrow(new BusinessException(
                ErrorCode.REFUND_CHANNEL_QUERY_FAILED, "remote detail must not enter operation log"));

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_FAILED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && "退款渠道查单未获得可信结果".equals(log.getOperationRemark())));
    }

    @Test
    void shouldNormalizeUnexpectedRefundChannelQueryFailureAndRecordBackoffHistory() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundChannelQueryService.query(source)).thenThrow(new IllegalStateException("remote timeout detail"));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("refund channel query failed unexpectedly", exception.getMessage());
        assertTrue(exception.getCause() instanceof IllegalStateException);
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_FAILED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && !log.getOperationRemark().contains("remote timeout detail")));
    }

    @Test
    void shouldRejectRateLimitedRefundBeforeChannelQueryWithoutFailureHistory() {
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        org.mockito.Mockito.doThrow(new BusinessException(
                ErrorCode.REFUND_CHANNEL_QUERY_RATE_LIMITED, "refund query rate limited"))
                .when(refundChannelQueryRateLimitService).acquire("wx_h5", "REF-001");

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_RATE_LIMITED, exception.getCode());
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .insertOperationLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSkipChannelQueryForFinalizedRefund() {
        RefundCallbackSourceDTO source = buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setRefundOrderId("REF-001");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        createService().query(queryRequest());

        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
    }

    @Test
    void shouldNotRegressFinalStatusWhenCallbackWinsDuringRefundQuery() {
        RefundCallbackSourceDTO snapshot = buildCallbackSource("PROCESSING", "wx_h5", "CHANNEL-REF-001");
        RefundCallbackSourceDTO finalized = buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001");
        RefundListItemDTO mapperItem = new RefundListItemDTO();
        mapperItem.setStatus("SUCCESS");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(snapshot);
        when(refundChannelQueryService.query(snapshot)).thenReturn(buildQueryResult("PROCESSING", "CHANNEL-REF-001"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(finalized);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        RefundListItemDTO result = createService().query(queryRequest());

        assertEquals("SUCCESS", result.getStatus());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldRejectMissingOrOversizedManualRefundOperatorBeforeMapperAccess() {
        RefundServiceImpl service = createService();
        String oversizedOperator = String.join("", Collections.nCopies(65, "A"));

        RefundApplyRequestDTO blankApply = new RefundApplyRequestDTO();
        blankApply.setOperator("   ");
        RefundApplyRequestDTO oversizedApply = new RefundApplyRequestDTO();
        oversizedApply.setOperator(oversizedOperator);
        RefundApplyRequestDTO controlApply = new RefundApplyRequestDTO();
        controlApply.setOperator("refund-operator\n");
        RefundActionRequestDTO blankAction = new RefundActionRequestDTO();
        blankAction.setOperator("   ");
        RefundActionRequestDTO oversizedAction = new RefundActionRequestDTO();
        oversizedAction.setOperator(oversizedOperator);
        RefundStatusQueryRequestDTO blankQuery = new RefundStatusQueryRequestDTO();
        blankQuery.setOperator("   ");
        RefundStatusQueryRequestDTO oversizedQuery = new RefundStatusQueryRequestDTO();
        oversizedQuery.setOperator(oversizedOperator);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.apply(blankApply));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.apply(oversizedApply));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.apply(controlApply));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(blankAction));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(oversizedAction));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.retry(blankAction));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.retry(oversizedAction));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.query(blankQuery));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.query(oversizedQuery));

        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    private RefundCallbackRequestDTO buildCallbackRequest(String status) {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId(" REF-001 ");
        request.setChannelRefundNo(" CHANNEL-REF-001 ");
        request.setRefundStatus(status);
        request.setTimestamp("1789000000");
        request.setNonce("nonce-refund-001");
        request.setSignature("signature");
        return request;
    }

    private RefundCallbackSourceDTO buildCallbackSource(String status, String channel, String channelRefundNo) {
        RefundCallbackSourceDTO source = new RefundCallbackSourceDTO();
        source.setRefundOrderId("REF-001");
        source.setStatus(status);
        source.setChannelCode(channel);
        source.setChannelRefundNo(channelRefundNo);
        return source;
    }

    private RefundStatusQueryRequestDTO queryRequest() {
        RefundStatusQueryRequestDTO request = new RefundStatusQueryRequestDTO();
        request.setRefundOrderId(" REF-001 ");
        request.setOperator(" refund-query-operator ");
        return request;
    }

    private RefundChannelQueryResultDTO buildQueryResult(String status, String channelRefundNo) {
        RefundChannelQueryResultDTO result = new RefundChannelQueryResultDTO();
        result.setRefundStatus(status);
        result.setChannelRefundNo(channelRefundNo);
        result.setQuerySource("TEST");
        return result;
    }

    private RefundServiceImpl createService() {
        return new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService, refundChannelQueryRateLimitService);
    }

    private void stubRetryCapacity(String refundOrderId,
                                   String paymentOrderId,
                                   BigDecimal refundAmount,
                                   BigDecimal activeRefundAmount,
                                   BigDecimal paidAmount) {
        RefundRetrySourceDTO retrySource = new RefundRetrySourceDTO();
        retrySource.setRefundOrderId(refundOrderId);
        retrySource.setPaymentOrderId(paymentOrderId);
        retrySource.setRefundAmount(refundAmount);
        retrySource.setStatus("FAIL");
        RefundPaymentSourceDTO paymentSource = new RefundPaymentSourceDTO();
        paymentSource.setPaymentOrderId(paymentOrderId);
        paymentSource.setPaidAmount(paidAmount);
        paymentSource.setStatus("SUCCESS");
        when(refundMapper.findRetrySourceForUpdate(refundOrderId)).thenReturn(retrySource);
        when(refundMapper.findPaymentSourceForUpdate(paymentOrderId)).thenReturn(paymentSource);
        when(refundMapper.sumActiveRefundAmount(paymentOrderId)).thenReturn(activeRefundAmount);
    }
}
