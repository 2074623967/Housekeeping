package com.abc123.refund.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.common.RefundEventHttpSignature;
import com.abc123.refund.dao.RefundDao;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOperationLogDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.dto.PageResultDTO;
import com.abc123.refund.entity.PaymentSuccessProjectionEntity;
import com.abc123.refund.entity.RefundOutboxEventEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 退款状态机和金额边界单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundDao refundDao;

    @Mock
    private RestTemplate restTemplate;

    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(refundDao, "test-operator");
    }

    @Test
    void shouldRejectRefundWhenPaymentSourceMissing() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));
        when(refundDao.findPaymentSourceForUpdate("PAY-1")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.apply(request));
    }

    @Test
    void shouldNormalizeNullListsAtRefundBoundaries() {
        when(refundDao.findList(any(RefundQueryDTO.class))).thenReturn(Arrays.asList((RefundListItemDTO) null));
        when(refundDao.count(any(RefundQueryDTO.class))).thenReturn(0L);
        PageResultDTO<RefundListItemDTO> page = service.list(new RefundQueryDTO());

        assertEquals(Collections.emptyList(), page.getItems());

        RefundListItemDTO item = new RefundListItemDTO();
        item.setRefundOrderId("REF-EMPTY");
        when(refundDao.findByRefundOrderId("REF-EMPTY")).thenReturn(item);
        when(refundDao.findLogs("REF-EMPTY")).thenReturn(Arrays.asList((com.abc123.refund.dto.RefundOperationLogDTO) null));
        when(refundDao.findOutboxList(any())).thenReturn(Arrays.asList((RefundOutboxItemDTO) null));

        byte[] snapshot = service.exportDetailSnapshot("REF-EMPTY");
        assertTrue(new String(snapshot, java.nio.charset.StandardCharsets.UTF_8).contains("操作日志"));
    }

    @Test
    void shouldKeepRefundSnapshotTextAsCsvData() {
        RefundListItemDTO item = new RefundListItemDTO();
        item.setRefundOrderId("REF-CSV");
        item.setCustomerName("＋1+1");
        item.setRefundReason("=1+1");
        RefundOperationLogDTO log = new RefundOperationLogDTO();
        log.setOperationRemark("note\r=1+1");
        RefundOutboxItemDTO outbox = new RefundOutboxItemDTO();
        outbox.setLastErrorMessage("@SUM(1)");
        when(refundDao.findByRefundOrderId("REF-CSV")).thenReturn(item);
        when(refundDao.findLogs("REF-CSV")).thenReturn(Collections.singletonList(log));
        when(refundDao.findOutboxList(any())).thenReturn(Collections.singletonList(outbox));

        String csv = new String(service.exportDetailSnapshot("REF-CSV"), StandardCharsets.UTF_8);

        assertTrue(csv.contains("'＋1+1"));
        assertTrue(csv.contains("'=1+1"));
        assertTrue(csv.contains("\"note\r=1+1\""));
        assertTrue(csv.contains("'@SUM(1)"));
        assertEquals("=1+1", item.getRefundReason());
    }

    @Test
    void shouldRejectRefundWhenAmountExceedsPaidAmount() {
        PaymentSuccessProjectionEntity source = source("PAY-1", "100.00");
        when(refundDao.findPaymentSourceForUpdate("PAY-1")).thenReturn(source);
        when(refundDao.sumActiveRefundAmount("PAY-1")).thenReturn(new BigDecimal("95.00"));
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));

        assertThrows(BusinessException.class, () -> service.apply(request));
    }

    @Test
    void shouldRejectRefundAmountWithMoreThanTwoDecimalPlacesBeforeReadingSource() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.001"));

        assertThrows(BusinessException.class, () -> service.apply(request));
        verify(refundDao, never()).findByIdempotencyKey(any());
        verify(refundDao, never()).findPaymentSourceForUpdate(any());
        verify(refundDao, never()).insertRefund(any());
    }

    @Test
    void shouldRejectRefundAmountBeyondDecimalColumnRangeBeforeReadingSource() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10000000000000000.00"));

        assertThrows(BusinessException.class, () -> service.apply(request));
        verify(refundDao, never()).findByIdempotencyKey(any());
        verify(refundDao, never()).findPaymentSourceForUpdate(any());
        verify(refundDao, never()).insertRefund(any());
    }

    @Test
    void shouldRejectInvalidExplicitIdempotencyKeysBeforeReadingSource() {
        for (String idempotencyKey : Arrays.asList(
                " ", "\t", String.join("", Collections.nCopies(129, "x")), "abc\nkey")) {
            RefundApplyRequestDTO request = new RefundApplyRequestDTO();
            request.setPaymentOrderId("PAY-1");
            request.setRefundAmount(new BigDecimal("10.00"));
            request.setIdempotencyKey(idempotencyKey);

            BusinessException exception = assertThrows(BusinessException.class, () -> service.apply(request));
            assertTrue(exception.getMessage().contains("幂等键"));
        }
        verifyNoInteractions(refundDao);
    }

    @Test
    void shouldCreateReviewingRefund() {
        PaymentSuccessProjectionEntity source = source("PAY-1", "100.00");
        when(refundDao.findPaymentSourceForUpdate("PAY-1")).thenReturn(source);
        when(refundDao.sumActiveRefundAmount("PAY-1")).thenReturn(BigDecimal.ZERO);
        when(refundDao.findByIdempotencyKey(any())).thenReturn(null);
        when(refundDao.insertRefund(any())).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("REVIEWING");
        when(refundDao.findByRefundOrderId(any())).thenReturn(result);
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));

        assertEquals("REVIEWING", service.apply(request).getStatus());
        verify(refundDao).insertLog(any(), eq("APPLY"), eq("发起退款申请"),
                eq("INIT"), eq("REVIEWING"), eq("test-operator"), any());
    }

    @Test
    void shouldGenerateDistinctRefundOrderIdsWithinSameMillisecond() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 10, 12, 12, 34, 56, 123_000_000);
        Set<String> ids = new HashSet<>();
        for (int attempt = 0; attempt < 128; attempt++) {
            String refundOrderId = RefundServiceImpl.newRefundOrderId(timestamp);
            assertTrue(refundOrderId.startsWith("REF20261012123456123"));
            assertTrue(refundOrderId.length() <= 64);
            assertTrue(ids.add(refundOrderId));
        }
    }

    @Test
    void shouldReturnIdenticalRefundIdempotencyReplayAfterLockingPaymentSource() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("10.00"));
        request.setRefundMethod("ORIGINAL");
        request.setRefundReason("服务取消");
        request.setIdempotencyKey("REF-KEY-1");
        RefundListItemDTO existing = new RefundListItemDTO();
        existing.setPaymentOrderId("PAY-1");
        existing.setRefundAmount(new BigDecimal("10.00"));
        existing.setRefundMethod("ORIGINAL");
        existing.setRefundReason("服务取消");
        when(refundDao.findPaymentSourceForUpdate("PAY-1")).thenReturn(source("PAY-1", "100.00"));
        when(refundDao.findByIdempotencyKey("REF-KEY-1")).thenReturn(existing);

        assertEquals(existing, service.apply(request));
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(refundDao);
        order.verify(refundDao).findPaymentSourceForUpdate("PAY-1");
        order.verify(refundDao).findByIdempotencyKey("REF-KEY-1");
        verify(refundDao, never()).sumActiveRefundAmount(any());
        verify(refundDao, never()).insertRefund(any());
    }

    @Test
    void shouldRejectConflictingRefundIdempotencyReplay() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-1");
        request.setRefundAmount(new BigDecimal("11.00"));
        request.setRefundMethod("ORIGINAL");
        request.setRefundReason("服务取消");
        request.setIdempotencyKey("REF-KEY-2");
        RefundListItemDTO existing = new RefundListItemDTO();
        existing.setPaymentOrderId("PAY-1");
        existing.setRefundAmount(new BigDecimal("10.00"));
        existing.setRefundMethod("ORIGINAL");
        existing.setRefundReason("服务取消");
        when(refundDao.findPaymentSourceForUpdate("PAY-1")).thenReturn(source("PAY-1", "100.00"));
        when(refundDao.findByIdempotencyKey("REF-KEY-2")).thenReturn(existing);

        assertThrows(BusinessException.class, () -> service.apply(request));
        verify(refundDao).findPaymentSourceForUpdate("PAY-1");
        verify(refundDao, never()).insertRefund(any());
    }

    @Test
    void shouldMoveReviewingToApproved() {
        when(refundDao.updateStatus("REF-1", "REVIEWING", "APPROVED", null)).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("APPROVED");
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-1");

        assertEquals("APPROVED", service.approve(request).getStatus());
    }

    @Test
    void shouldRejectInvalidTransition() {
        when(refundDao.updateStatus("REF-1", "APPROVED", "PROCESSING", null)).thenReturn(0);
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-1");

        assertThrows(BusinessException.class, () -> service.submit(request));
    }

    @Test
    void shouldHandleSuccessCallback() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-1", null)).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("SUCCESS");
        result.setPaymentOrderId("PAY-1");
        result.setPayScene("HOME_CLEAN");
        result.setRefundAmount(new BigDecimal("10.00"));
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        when(refundDao.insertSuccessOutbox("REF-1", "PAY-1", "HOME_CLEAN", new BigDecimal("10.00")))
                .thenReturn(1);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1");

        assertEquals("SUCCESS", service.callback(request).getStatus());
        verify(refundDao).insertSuccessOutbox("REF-1", "PAY-1", "HOME_CLEAN", new BigDecimal("10.00"));
    }

    @Test
    void shouldRejectSuccessCallbackWhenOutboxInsertAffectsNoRows() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-1", null)).thenReturn(1);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setPaymentOrderId("PAY-1");
        result.setRefundAmount(new BigDecimal("10.00"));
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1");

        assertThrows(IllegalStateException.class, () -> service.callback(request));
        verify(refundDao).insertSuccessOutbox("REF-1", "PAY-1", null, new BigDecimal("10.00"));
    }

    @Test
    void shouldRejectSuccessCallbackWithoutChannelRefundIdWithoutChangingRefund() {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-MISSING-CHANNEL-ID");
        request.setResult("SUCCESS");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verify(refundDao, never()).updateCallback(any(), any(), any(), any());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any(), any());
    }

    @Test
    void shouldRejectOversizedCallbackFactsBeforeChangingRefund() {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId(String.join("", Collections.nCopies(129, "C")));
        assertThrows(BusinessException.class, () -> service.callback(request));

        request.setChannelRefundId("CH-1");
        request.setResult("FAIL");
        request.setFailureCode(String.join("", Collections.nCopies(65, "E")));
        assertThrows(BusinessException.class, () -> service.callback(request));

        request.setFailureCode("E001");
        request.setRawMessage(String.join("", Collections.nCopies(501, "R")));
        assertThrows(BusinessException.class, () -> service.callback(request));
        verifyNoInteractions(refundDao);
    }

    @Test
    void shouldRejectControlCharactersInChannelRefundIdentity() {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1\nCH-2");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verifyNoInteractions(refundDao);
    }

    @Test
    void shouldReturnExistingRefundWhenSuccessCallbackRepeated() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-1", null)).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("SUCCESS");
        result.setRefundOrderId("REF-1");
        result.setChannelRefundId("CH-1");
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-1");

        assertEquals("SUCCESS", service.callback(request).getStatus());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any(), any());
    }

    @Test
    void shouldRejectSuccessCallbackWithConflictingChannelRefundId() {
        when(refundDao.updateCallback("REF-1", "SUCCESS", "CH-2", null)).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("SUCCESS");
        result.setRefundOrderId("REF-1");
        result.setChannelRefundId("CH-1");
        when(refundDao.findByRefundOrderId("REF-1")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-1");
        request.setResult("SUCCESS");
        request.setChannelRefundId("CH-2");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any(), any());
    }

    @Test
    void shouldReturnExistingRefundWhenFailCallbackRepeated() {
        when(refundDao.updateCallback("REF-2", "FAIL", "CH-2", "E001")).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("FAIL");
        result.setRefundOrderId("REF-2");
        result.setChannelRefundId("CH-2");
        result.setFailureCode("E001");
        when(refundDao.findByRefundOrderId("REF-2")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-2");
        request.setResult("FAIL");
        request.setChannelRefundId("CH-2");
        request.setFailureCode("E001");

        assertEquals("FAIL", service.callback(request).getStatus());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectFailCallbackWithConflictingFailureCode() {
        when(refundDao.updateCallback("REF-2", "FAIL", "CH-2", "E002")).thenReturn(0);
        RefundListItemDTO result = new RefundListItemDTO();
        result.setStatus("FAIL");
        result.setRefundOrderId("REF-2");
        result.setChannelRefundId("CH-2");
        result.setFailureCode("E001");
        when(refundDao.findByRefundOrderId("REF-2")).thenReturn(result);
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-2");
        request.setResult("FAIL");
        request.setChannelRefundId("CH-2");
        request.setFailureCode("E002");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectUnknownCallbackResultWithoutChangingRefund() {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-UNKNOWN");
        request.setResult(" UNKNOWN ");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verify(refundDao, never()).updateCallback(any(), any(), any(), any());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any(), any());
    }

    @Test
    void shouldRejectMissingCallbackResultWithoutChangingRefund() {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId("REF-MISSING-RESULT");

        assertThrows(BusinessException.class, () -> service.callback(request));
        verify(refundDao, never()).updateCallback(any(), any(), any(), any());
        verify(refundDao, never()).insertLog(any(), any(), any(), any(), any(), any(), any());
        verify(refundDao, never()).insertSuccessOutbox(any(), any(), any(), any());
    }

    @Test
    void shouldProjectPaymentSuccessIdempotently() {
        PaymentSuccessProjectionDTO request = new PaymentSuccessProjectionDTO();
        request.setPaymentOrderId("PAY-2");
        request.setOrderNo("ORDER-2");
        request.setPaidAmount(new BigDecimal("20.00"));
        when(refundDao.insertPaymentSource(any(PaymentSuccessProjectionEntity.class))).thenReturn(1);

        service.projectPaymentSuccess(request);

        verify(refundDao).insertPaymentSource(any(PaymentSuccessProjectionEntity.class));
    }

    @Test
    void shouldAcceptIdenticalPaymentSuccessReplayWithoutWritingAgain() {
        PaymentSuccessProjectionDTO request = new PaymentSuccessProjectionDTO();
        request.setPaymentOrderId("PAY-REPLAY");
        request.setOrderNo("ORDER-REPLAY");
        request.setCustomerName("客户");
        request.setPaidAmount(new BigDecimal("20.00"));
        request.setChannelCode("WECHAT");
        when(refundDao.findPaymentSource("PAY-REPLAY"))
                .thenReturn(source("PAY-REPLAY", "20.00", "ORDER-REPLAY", "客户", "WECHAT"));

        service.projectPaymentSuccess(request);

        verify(refundDao, never()).insertPaymentSource(any(PaymentSuccessProjectionEntity.class));
    }

    @Test
    void shouldRejectConflictingPaymentSuccessReplay() {
        PaymentSuccessProjectionDTO request = new PaymentSuccessProjectionDTO();
        request.setPaymentOrderId("PAY-CONFLICT");
        request.setOrderNo("ORDER-CONFLICT");
        request.setCustomerName("客户");
        request.setPaidAmount(new BigDecimal("21.00"));
        request.setChannelCode("WECHAT");
        when(refundDao.findPaymentSource("PAY-CONFLICT"))
                .thenReturn(source("PAY-CONFLICT", "20.00", "ORDER-CONFLICT", "客户", "WECHAT"));

        assertThrows(BusinessException.class, () -> service.projectPaymentSuccess(request));
        verify(refundDao, never()).insertPaymentSource(any(PaymentSuccessProjectionEntity.class));
    }

    @Test
    void shouldRejectPaymentSuccessAmountWithMoreThanTwoDecimalPlacesBeforeReadingSource() {
        PaymentSuccessProjectionDTO request = new PaymentSuccessProjectionDTO();
        request.setPaymentOrderId("PAY-SCALE");
        request.setOrderNo("ORDER-SCALE");
        request.setPaidAmount(new BigDecimal("20.001"));

        assertThrows(BusinessException.class, () -> service.projectPaymentSuccess(request));
        verify(refundDao, never()).findPaymentSource(any());
        verify(refundDao, never()).insertPaymentSource(any(PaymentSuccessProjectionEntity.class));
    }

    @Test
    void shouldDispatchOutboxSuccessfully() {
        RefundOutboxEventEntity event = new RefundOutboxEventEntity();
        event.setEventId("REVT-1");
        event.setStatus("PENDING");
        when(refundDao.findOutboxByEventId("REVT-1")).thenReturn(event);
        when(refundDao.markOutboxSent("REVT-1")).thenReturn(1);
        when(refundDao.markOutboxDispatching("REVT-1")).thenReturn(1);
        RefundOutboxEventEntity sent = outboxEvent("REVT-1", "REF-1");
        sent.setStatus("SENT");
        when(refundDao.findOutboxByEventId("REVT-1")).thenReturn(event, sent);

        RefundOutboxDispatchRequestDTO request = new RefundOutboxDispatchRequestDTO();
        request.setSimulateResult("SUCCESS");

        assertEquals("SENT", service.dispatchOutbox("REVT-1", request).getStatus());
        verify(refundDao).markOutboxSent("REVT-1");
    }

    @Test
    void shouldMarkOutboxFailedWhenDispatchFails() {
        RefundOutboxEventEntity event = new RefundOutboxEventEntity();
        event.setEventId("REVT-2");
        event.setStatus("PENDING");
        when(refundDao.findOutboxByEventId("REVT-2")).thenReturn(event);
        when(refundDao.markOutboxFailed("REVT-2", "账务系统暂不可用")).thenReturn(1);
        when(refundDao.markOutboxDispatching("REVT-2")).thenReturn(1);
        RefundOutboxEventEntity failed = outboxEvent("REVT-2", "REF-2");
        failed.setStatus("FAIL");
        failed.setRetryCount(1);
        when(refundDao.findOutboxByEventId("REVT-2")).thenReturn(event, failed);

        RefundOutboxDispatchRequestDTO request = new RefundOutboxDispatchRequestDTO();
        request.setSimulateResult("FAIL");
        request.setRemark("账务系统暂不可用");

        assertEquals("FAIL", service.dispatchOutbox("REVT-2", request).getStatus());
        verify(refundDao).markOutboxFailed("REVT-2", "账务系统暂不可用");
    }

    @Test
    void shouldDispatchRefundSuccessToAccountingAndReconciliation() {
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate, "local-refund-event-test-secret");
        RefundOutboxEventEntity event = outboxEvent("REVT-3", "REF-3");
        RefundListItemDTO refund = successfulRefund("REF-3", " PAY-3 ", "10.00");
        refund.setPayScene("HOME_CLEAN");
        when(refundDao.findOutboxByEventId("REVT-3")).thenReturn(event);
        when(refundDao.findByRefundOrderId("REF-3")).thenReturn(refund);
        when(restTemplate.postForEntity(eq("http://accounting/refunds/success"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "0")));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "0")));
        when(refundDao.markOutboxSent("REVT-3")).thenReturn(1);
        when(refundDao.markOutboxDispatching("REVT-3")).thenReturn(1);
        RefundOutboxEventEntity sent = outboxEvent("REVT-3", "REF-3");
        sent.setStatus("SENT");
        when(refundDao.findOutboxByEventId("REVT-3")).thenReturn(event, sent);

        RefundOutboxItemDTO result = service.dispatchOutbox("REVT-3", new RefundOutboxDispatchRequestDTO());

        assertEquals("SENT", result.getStatus());
        verify(restTemplate).postForEntity(eq("http://accounting/refunds/success"), any(), eq(java.util.Map.class));
        verify(restTemplate).postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class));
        org.mockito.ArgumentCaptor<Object> accountingPayload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://accounting/refunds/success"), accountingPayload.capture(), eq(java.util.Map.class));
        assertEquals("CHAIN-PAY-3",
                signedBody(accountingPayload.getValue()).get("businessChainId").asText());
        assertEquals("HOME_CLEAN",
                signedBody(accountingPayload.getValue()).get("payScene").asText());
        org.mockito.ArgumentCaptor<Object> reconciliationPayload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://reconciliation/facts"), reconciliationPayload.capture(), eq(java.util.Map.class));
        assertEquals("CHAIN-PAY-3",
                new ObjectMapper().valueToTree(reconciliationPayload.getValue()).get("businessChainId").asText());
        assertEquals("退款成功事实，原支付单号=PAY-3，支付场景=HOME_CLEAN",
                new ObjectMapper().valueToTree(reconciliationPayload.getValue()).get("payload").asText());
    }

    @Test
    void shouldSignExactRefundSuccessBodyForAccounting() throws Exception {
        String secret = "local-refund-event-test-secret";
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate, secret);
        RefundOutboxEventEntity event = outboxEvent("REVT-SIGNED", "REF-SIGNED");
        when(refundDao.findOutboxByEventId("REVT-SIGNED")).thenReturn(event);
        when(refundDao.findByRefundOrderId("REF-SIGNED"))
                .thenReturn(successfulRefund("REF-SIGNED", "PAY-SIGNED", "10.00"));
        when(refundDao.markOutboxDispatching("REVT-SIGNED")).thenReturn(1);
        when(refundDao.markOutboxSent("REVT-SIGNED")).thenReturn(1);
        RefundOutboxEventEntity sent = outboxEvent("REVT-SIGNED", "REF-SIGNED");
        sent.setStatus("SENT");
        when(refundDao.findOutboxByEventId("REVT-SIGNED")).thenReturn(event, sent);
        when(restTemplate.postForEntity(eq("http://accounting/refunds/success"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "0")));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "0")));

        assertEquals("SENT", service.dispatchOutbox("REVT-SIGNED", new RefundOutboxDispatchRequestDTO()).getStatus());

        org.mockito.ArgumentCaptor<Object> request = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://accounting/refunds/success"), request.capture(), eq(java.util.Map.class));
        assertTrue(request.getValue() instanceof HttpEntity);
        HttpEntity<?> signed = (HttpEntity<?>) request.getValue();
        String body = (String) signed.getBody();
        String timestamp = signed.getHeaders().getFirst(RefundEventHttpSignature.TIMESTAMP_HEADER_NAME);
        assertTrue(Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) <= 5);
        assertEquals(RefundEventHttpSignature.sign(body, timestamp, secret),
                signed.getHeaders().getFirst(RefundEventHttpSignature.HEADER_NAME));
        assertEquals("REF-SIGNED", new ObjectMapper().readTree(body).get("refundOrderId").asText());
    }

    @Test
    void shouldNotSendUnsignedRefundSuccessWhenHttpSecretIsMissing() {
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate);
        RefundOutboxEventEntity event = outboxEvent("REVT-UNSIGNED", "REF-UNSIGNED");
        when(refundDao.findByRefundOrderId("REF-UNSIGNED"))
                .thenReturn(successfulRefund("REF-UNSIGNED", "PAY-UNSIGNED", "10.00"));
        when(refundDao.markOutboxDispatching("REVT-UNSIGNED")).thenReturn(1);
        when(refundDao.markOutboxFailed(eq("REVT-UNSIGNED"), any())).thenReturn(1);
        RefundOutboxEventEntity failed = outboxEvent("REVT-UNSIGNED", "REF-UNSIGNED");
        failed.setStatus("FAIL");
        when(refundDao.findOutboxByEventId("REVT-UNSIGNED")).thenReturn(event, failed);

        assertEquals("FAIL", service.dispatchOutbox("REVT-UNSIGNED", new RefundOutboxDispatchRequestDTO()).getStatus());

        verifyNoInteractions(restTemplate);
        verify(refundDao).markOutboxFailed("REVT-UNSIGNED", "下游派发失败:IllegalStateException");
        verify(refundDao, never()).markOutboxSent("REVT-UNSIGNED");
    }

    @Test
    void shouldNotSendUnsignedRefundSuccessWhenHttpSecretIsBlank() {
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate, "  ");
        RefundOutboxEventEntity event = outboxEvent("REVT-BLANK", "REF-BLANK");
        when(refundDao.findByRefundOrderId("REF-BLANK"))
                .thenReturn(successfulRefund("REF-BLANK", "PAY-BLANK", "10.00"));
        when(refundDao.markOutboxDispatching("REVT-BLANK")).thenReturn(1);
        when(refundDao.markOutboxFailed("REVT-BLANK", "下游派发失败:IllegalStateException")).thenReturn(1);
        RefundOutboxEventEntity failed = outboxEvent("REVT-BLANK", "REF-BLANK");
        failed.setStatus("FAIL");
        when(refundDao.findOutboxByEventId("REVT-BLANK")).thenReturn(event, failed);

        assertEquals("FAIL", service.dispatchOutbox("REVT-BLANK", new RefundOutboxDispatchRequestDTO()).getStatus());

        verifyNoInteractions(restTemplate);
        verify(refundDao, never()).markOutboxSent("REVT-BLANK");
    }

    @Test
    void shouldKeepOutboxFailedWhenARealDownstreamCallFails() {
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate, "local-refund-event-test-secret");
        RefundOutboxEventEntity event = outboxEvent("REVT-4", "REF-4");
        when(refundDao.findOutboxByEventId("REVT-4")).thenReturn(event);
        when(refundDao.findByRefundOrderId("REF-4"))
                .thenReturn(successfulRefund("REF-4", "PAY-4", "10.00"));
        when(restTemplate.postForEntity(eq("http://accounting/refunds/success"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "0")));
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(java.util.Map.class)))
                .thenThrow(new IllegalStateException("downstream unavailable"));
        when(refundDao.markOutboxFailed("REVT-4", "下游派发失败:IllegalStateException")).thenReturn(1);
        when(refundDao.markOutboxDispatching("REVT-4")).thenReturn(1);
        RefundOutboxEventEntity failed = outboxEvent("REVT-4", "REF-4");
        failed.setStatus("FAIL");
        when(refundDao.findOutboxByEventId("REVT-4")).thenReturn(event, failed);

        RefundOutboxItemDTO result = service.dispatchOutbox("REVT-4", new RefundOutboxDispatchRequestDTO());

        assertEquals("FAIL", result.getStatus());
        verify(refundDao).markOutboxFailed("REVT-4", "下游派发失败:IllegalStateException");
        verify(refundDao, never()).markOutboxSent("REVT-4");
    }

    @Test
    void shouldRejectHttpSuccessWithFailedBusinessCode() {
        service = new RefundServiceImpl(refundDao, "test-operator", "HTTP",
                "http://accounting/refunds/success", "ACT10003",
                "http://reconciliation/facts", restTemplate, "local-refund-event-test-secret");
        RefundOutboxEventEntity event = outboxEvent("REVT-5", "REF-5");
        when(refundDao.findOutboxByEventId("REVT-5")).thenReturn(event);
        when(refundDao.findByRefundOrderId("REF-5"))
                .thenReturn(successfulRefund("REF-5", "PAY-5", "10.00"));
        when(restTemplate.postForEntity(eq("http://accounting/refunds/success"), any(), eq(java.util.Map.class)))
                .thenReturn(ResponseEntity.ok(Collections.singletonMap("code", "ACCOUNTING_ERROR")));
        when(refundDao.markOutboxFailed("REVT-5", "下游派发失败:IllegalStateException")).thenReturn(1);
        when(refundDao.markOutboxDispatching("REVT-5")).thenReturn(1);
        RefundOutboxEventEntity failed = outboxEvent("REVT-5", "REF-5");
        failed.setStatus("FAIL");
        when(refundDao.findOutboxByEventId("REVT-5")).thenReturn(event, failed);

        RefundOutboxItemDTO result = service.dispatchOutbox("REVT-5", new RefundOutboxDispatchRequestDTO());

        assertEquals("FAIL", result.getStatus());
        verify(refundDao, never()).markOutboxSent("REVT-5");
        verify(restTemplate, never()).postForEntity(
                eq("http://reconciliation/facts"), any(), eq(java.util.Map.class));
    }

    private RefundOutboxEventEntity outboxEvent(String eventId, String aggregateId) {
        RefundOutboxEventEntity event = new RefundOutboxEventEntity();
        event.setEventId(eventId);
        event.setAggregateId(aggregateId);
        event.setStatus("PENDING");
        return event;
    }

    private com.fasterxml.jackson.databind.JsonNode signedBody(Object request) {
        assertTrue(request instanceof HttpEntity);
        try {
            return new ObjectMapper().readTree((String) ((HttpEntity<?>) request).getBody());
        } catch (Exception exception) {
            throw new AssertionError("退款成功事件请求体不是 JSON", exception);
        }
    }

    private RefundListItemDTO successfulRefund(String refundOrderId, String paymentOrderId, String amount) {
        RefundListItemDTO refund = new RefundListItemDTO();
        refund.setRefundOrderId(refundOrderId);
        refund.setPaymentOrderId(paymentOrderId);
        refund.setRefundAmount(new BigDecimal(amount));
        refund.setStatus("SUCCESS");
        return refund;
    }

    private PaymentSuccessProjectionEntity source(String paymentOrderId, String amount) {
        return source(paymentOrderId, amount, "ORDER-1", "客户", null);
    }

    private PaymentSuccessProjectionEntity source(String paymentOrderId, String amount,
                                                  String orderNo, String customerName, String channelCode) {
        PaymentSuccessProjectionEntity source = new PaymentSuccessProjectionEntity();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo(orderNo);
        source.setCustomerName(customerName);
        source.setPaidAmount(new BigDecimal(amount));
        source.setChannelCode(channelCode);
        return source;
    }
}
