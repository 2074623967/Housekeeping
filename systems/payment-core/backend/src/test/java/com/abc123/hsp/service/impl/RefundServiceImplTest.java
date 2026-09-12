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
import com.abc123.hsp.dto.RefundAttemptItemDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundOperationLogItemDTO;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundApplicationIdempotencyDTO;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void shouldRejectInvalidRefundQueryFieldsBeforeMapperAccess() {
        RefundServiceImpl service = createService();
        RefundQueryDTO oversizedRefundOrderId = refundQuery(
                String.join("", Collections.nCopies(65, "R")), null, null, null);
        RefundQueryDTO controlRefundOrderId = refundQuery("REF-001\n", null, null, null);
        RefundQueryDTO oversizedPaymentOrderId = refundQuery(
                null, String.join("", Collections.nCopies(65, "P")), null, null);
        RefundQueryDTO controlPaymentOrderId = refundQuery(null, "PAY-001\n", null, null);
        RefundQueryDTO oversizedStatus = refundQuery(
                null, null, String.join("", Collections.nCopies(33, "S")), null);
        RefundQueryDTO controlStatus = refundQuery(null, null, "SUCCESS\n", null);
        RefundQueryDTO oversizedMethod = refundQuery(
                null, null, null, String.join("", Collections.nCopies(33, "M")));
        RefundQueryDTO controlMethod = refundQuery(null, null, null, "原路退款\n");

        assertEquals("退款单号长度不能超过64字符", queryFailure(service, oversizedRefundOrderId).getMessage());
        assertEquals("退款单号不能包含控制字符", queryFailure(service, controlRefundOrderId).getMessage());
        assertEquals("支付单号长度不能超过64字符", queryFailure(service, oversizedPaymentOrderId).getMessage());
        assertEquals("支付单号不能包含控制字符", queryFailure(service, controlPaymentOrderId).getMessage());
        assertEquals("退款状态长度不能超过32字符", queryFailure(service, oversizedStatus).getMessage());
        assertEquals("退款状态不能包含控制字符", queryFailure(service, controlStatus).getMessage());
        assertEquals("退款方式长度不能超过32字符", queryFailure(service, oversizedMethod).getMessage());
        assertEquals("退款方式不能包含控制字符", queryFailure(service, controlMethod).getMessage());
        IllegalArgumentException exportFailure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.exportCsv(controlRefundOrderId));
        assertEquals("退款单号不能包含控制字符", exportFailure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldApplyRefundForSuccessfulPayment() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setIdempotencyKey("REFUND-APPLY-001");
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
    void shouldReturnExistingRefundForSameIdempotentApplication() {
        RefundApplyRequestDTO request = refundApplyRequest(new BigDecimal("20.00"));
        request.setRefundReason("客户取消服务");
        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        RefundApplicationIdempotencyDTO existing = new RefundApplicationIdempotencyDTO();
        existing.setRefundOrderId("REF-EXISTING");
        existing.setPaymentOrderId("PAY-001");
        existing.setRefundAmount(new BigDecimal("20.0"));
        existing.setRefundMethod("原路退款");
        existing.setRefundReason("客户取消服务");
        existing.setOperator("refund-applicant");
        RefundListItemDTO existingItem = new RefundListItemDTO();
        existingItem.setRefundOrderId("REF-EXISTING");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.findApplicationByIdempotencyKey("PAY-001", "REFUND-APPLY-001"))
                .thenReturn(existing);
        when(refundMapper.findByRefundOrderId("REF-EXISTING")).thenReturn(existingItem);

        RefundListItemDTO result = createService().apply(request);

        assertEquals("REF-EXISTING", result.getRefundOrderId());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .sumActiveRefundAmount(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).insertRefund(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .insertOperationLog(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectDifferentApplicationContentUsingSameIdempotencyKey() {
        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        RefundApplicationIdempotencyDTO existing = new RefundApplicationIdempotencyDTO();
        existing.setRefundOrderId("REF-EXISTING");
        existing.setPaymentOrderId("PAY-001");
        existing.setRefundAmount(new BigDecimal("20.00"));
        existing.setRefundMethod("原路退款");
        existing.setRefundReason("");
        existing.setOperator("refund-applicant");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.findApplicationByIdempotencyKey("PAY-001", "REFUND-APPLY-001"))
                .thenReturn(existing);

        RefundApplyRequestDTO differentAmount = refundApplyRequest(new BigDecimal("19.00"));
        RefundApplyRequestDTO differentMethod = refundApplyRequest(new BigDecimal("20.00"));
        differentMethod.setRefundMethod("线下打款");
        RefundApplyRequestDTO differentReason = refundApplyRequest(new BigDecimal("20.00"));
        differentReason.setRefundReason("不同退款原因");
        RefundApplyRequestDTO differentOperator = refundApplyRequest(new BigDecimal("20.00"));
        differentOperator.setOperator("another-applicant");

        for (RefundApplyRequestDTO request : Arrays.asList(
                differentAmount, differentMethod, differentReason, differentOperator)) {
            BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                    BusinessException.class, () -> createService().apply(request));
            assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_CONFLICT, exception.getCode());
        }
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .sumActiveRefundAmount(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).insertRefund(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldBuildRefundBusinessNoInBusinessTimezone() {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate businessToday = LocalDate.now(businessZone);
        ZoneId alternativeZone = ZoneId.of("Pacific/Kiritimati");
        if (businessToday.equals(LocalDate.now(alternativeZone))) {
            alternativeZone = ZoneId.of("Etc/GMT+12");
        }
        assertFalse(businessToday.equals(LocalDate.now(alternativeZone)));

        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(alternativeZone));

            com.abc123.hsp.entity.RefundOperationLogEntity log = RefundServiceImpl.buildOperationLog(
                    "REF-001", "APPLY", "发起退款", "INIT", "REVIEWING", "finance-ops", "退款申请");

            assertTrue(log.getLogNo().startsWith(
                    "ROL" + businessToday.format(DateTimeFormatter.BASIC_ISO_DATE)));
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void shouldRejectRefundWhenAmountExceedsPaidAmount() {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setIdempotencyKey("REFUND-APPLY-EXCESSIVE");
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
    void shouldRejectRefundAmountOutsideDatabaseCapacity() {
        RefundApplyRequestDTO excessiveScale = refundApplyRequest(new BigDecimal("0.001"));
        IllegalArgumentException scale = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> createService().apply(excessiveScale));
        assertEquals("退款金额最多支持 16 位整数和 2 位小数", scale.getMessage());

        RefundApplyRequestDTO excessiveAmount = refundApplyRequest(
                new BigDecimal("10000000000000000.00"));
        IllegalArgumentException amount = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> createService().apply(excessiveAmount));
        assertEquals("退款金额最多支持 16 位整数和 2 位小数", amount.getMessage());

        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldAllowRefundAmountAtDatabaseCapacityBoundary() {
        RefundApplyRequestDTO maximum = refundApplyRequest(new BigDecimal("9999999999999999.990"));
        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setOrderNo("ORD-001");
        source.setPaidAmount(new BigDecimal("9999999999999999.99"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(BigDecimal.ZERO);

        createService().apply(maximum);

        verify(refundMapper).insertRefund(
                org.mockito.ArgumentMatchers.startsWith("REF"),
                org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.eq(maximum),
                org.mockito.ArgumentMatchers.eq("REVIEWING"),
                org.mockito.ArgumentMatchers.eq("warn"));
    }

    @Test
    void shouldNormalizeRefundApplyTextFieldsBeforePersistence() {
        RefundApplyRequestDTO request = refundApplyRequest(new BigDecimal("20.00"));
        request.setPaymentOrderId(" PAY-001 ");
        request.setIdempotencyKey(" REFUND-APPLY-001 ");
        request.setRefundMethod(" 原路退款 ");
        request.setRefundReason(null);
        RefundPaymentSourceDTO source = new RefundPaymentSourceDTO();
        source.setPaymentOrderId("PAY-001");
        source.setPaidAmount(new BigDecimal("20.00"));
        source.setStatus("SUCCESS");
        when(refundMapper.findPaymentSourceForUpdate("PAY-001")).thenReturn(source);
        when(refundMapper.sumActiveRefundAmount("PAY-001")).thenReturn(BigDecimal.ZERO);

        createService().apply(request);

        assertEquals("PAY-001", request.getPaymentOrderId());
        assertEquals("REFUND-APPLY-001", request.getIdempotencyKey());
        assertEquals("原路退款", request.getRefundMethod());
        assertEquals("", request.getRefundReason());
        verify(refundMapper).insertRefund(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.eq(request),
                org.mockito.ArgumentMatchers.eq("REVIEWING"),
                org.mockito.ArgumentMatchers.eq("warn"));
    }

    @Test
    void shouldRejectInvalidRefundApplyTextFieldsBeforeMapperAccess() {
        RefundApplyRequestDTO oversizedPaymentOrderId = refundApplyRequest(new BigDecimal("20.00"));
        oversizedPaymentOrderId.setPaymentOrderId(String.join("", Collections.nCopies(65, "P")));
        RefundApplyRequestDTO invalidPaymentOrderId = refundApplyRequest(new BigDecimal("20.00"));
        invalidPaymentOrderId.setPaymentOrderId("PAY-001\n");
        RefundApplyRequestDTO oversizedMethod = refundApplyRequest(new BigDecimal("20.00"));
        oversizedMethod.setRefundMethod(String.join("", Collections.nCopies(33, "M")));
        RefundApplyRequestDTO invalidMethod = refundApplyRequest(new BigDecimal("20.00"));
        invalidMethod.setRefundMethod("原路退款\n");
        RefundApplyRequestDTO oversizedReason = refundApplyRequest(new BigDecimal("20.00"));
        oversizedReason.setRefundReason(String.join("", Collections.nCopies(256, "R")));
        RefundApplyRequestDTO invalidReason = refundApplyRequest(new BigDecimal("20.00"));
        invalidReason.setRefundReason("客户取消\n服务");

        assertEquals("原支付单号长度不能超过64字符", applyFailure(oversizedPaymentOrderId).getMessage());
        assertEquals("原支付单号不能包含控制字符", applyFailure(invalidPaymentOrderId).getMessage());
        assertEquals("退款方式长度不能超过32字符", applyFailure(oversizedMethod).getMessage());
        assertEquals("退款方式不能包含控制字符", applyFailure(invalidMethod).getMessage());
        assertEquals("退款原因长度不能超过255字符", applyFailure(oversizedReason).getMessage());
        assertEquals("退款原因不能包含控制字符", applyFailure(invalidReason).getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldRejectInvalidRefundApplicationIdempotencyKeyBeforeMapperAccess() {
        RefundApplyRequestDTO missing = refundApplyRequest(new BigDecimal("20.00"));
        missing.setIdempotencyKey("   ");
        RefundApplyRequestDTO oversized = refundApplyRequest(new BigDecimal("20.00"));
        oversized.setIdempotencyKey(String.join("", Collections.nCopies(129, "I")));
        RefundApplyRequestDTO control = refundApplyRequest(new BigDecimal("20.00"));
        control.setIdempotencyKey("REFUND-APPLY\n001");

        assertInvalidApplicationIdempotencyKey(missing, "退款申请幂等键不能为空");
        assertInvalidApplicationIdempotencyKey(oversized, "退款申请幂等键长度不能超过 128 个字符");
        assertInvalidApplicationIdempotencyKey(control, "退款申请幂等键不能包含控制字符");
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldApproveReviewingRefund() {
        RefundActionRequestDTO request = new RefundActionRequestDTO();
        request.setRefundOrderId("REF-001");
        request.setOperator(" refund-approver ");
        request.setRemark(" 确认退款 ");
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

        ArgumentCaptor<String> refundAttemptNoCaptor = ArgumentCaptor.forClass(String.class);
        verify(refundMapper).insertRefundAttempt(
                refundAttemptNoCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("REF-001"),
                org.mockito.ArgumentMatchers.eq("wx_h5"));
        String refundAttemptNo = refundAttemptNoCaptor.getValue();
        assertTrue(refundAttemptNo.startsWith("RFA"));
        verify(refundMapper).upsertRefundSubmitEvent(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq(refundAttemptNo),
                org.mockito.ArgumentMatchers.eq("PAY-001"));
        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.eq(refundAttemptNo),
                org.mockito.ArgumentMatchers.eq("refund-approver"));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "APPROVE".equals(log.getActionCode())
                        && "refund-approver".equals(log.getOperatorName())
                        && "确认退款".equals(log.getOperationRemark())));
        verify(refundMapper).findByRefundOrderId("REF-001");
    }

    @Test
    void shouldRejectInvalidRefundOrderIdBeforeMapperAccess() {
        RefundServiceImpl service = createService();
        RefundActionRequestDTO oversizedAction = new RefundActionRequestDTO();
        oversizedAction.setRefundOrderId(String.join("", Collections.nCopies(65, "R")));
        oversizedAction.setOperator("refund-approver");
        RefundActionRequestDTO controlAction = new RefundActionRequestDTO();
        controlAction.setRefundOrderId("REF-001\n");
        controlAction.setOperator("refund-approver");

        IllegalArgumentException oversized = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(oversizedAction));
        IllegalArgumentException control = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(controlAction));
        IllegalArgumentException oversizedDetail = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.detail(String.join("", Collections.nCopies(65, "R"))));
        IllegalArgumentException controlDetail = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.detail("REF-001\n"));

        assertEquals("退款单号长度不能超过64字符", oversized.getMessage());
        assertEquals("退款单号不能包含控制字符", control.getMessage());
        assertEquals("退款单号长度不能超过64字符", oversizedDetail.getMessage());
        assertEquals("退款单号不能包含控制字符", controlDetail.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldRejectInvalidRefundActionRemarkBeforeStatusUpdate() {
        RefundServiceImpl service = createService();
        RefundActionRequestDTO oversized = new RefundActionRequestDTO();
        oversized.setRefundOrderId("REF-001");
        oversized.setOperator("refund-approver");
        oversized.setRemark(String.join("", Collections.nCopies(256, "R")));
        RefundActionRequestDTO control = new RefundActionRequestDTO();
        control.setRefundOrderId("REF-001");
        control.setOperator("refund-approver");
        control.setRemark("确认退款\n");

        IllegalArgumentException oversizedFailure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(oversized));
        IllegalArgumentException controlFailure = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.approve(control));

        assertEquals("操作备注长度不能超过255字符", oversizedFailure.getMessage());
        assertEquals("操作备注不能包含控制字符", controlFailure.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
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
                        org.mockito.ArgumentMatchers.startsWith("RFA"),
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
                    org.mockito.ArgumentMatchers.startsWith("RFA"),
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
        when(refundMapper.restartFailedRefund("REF-002")).thenReturn(1);
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

        verify(refundMapper).restartFailedRefund("REF-002");
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                "REF-002", "FAIL", "PROCESSING", "warn", false);
        verify(refundMapper).upsertRefundSubmitEvent(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.startsWith("RFA"),
                org.mockito.ArgumentMatchers.eq("PAY-002"));
        verify(refundMapper).insertRefundAttempt(
                org.mockito.ArgumentMatchers.startsWith("RFA"),
                org.mockito.ArgumentMatchers.eq("REF-002"),
                org.mockito.ArgumentMatchers.eq("alipay_h5"));
        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.startsWith("RFA"),
                org.mockito.ArgumentMatchers.eq("refund-retry-operator"));
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "RETRY".equals(log.getActionCode())
                        && "refund-retry-operator".equals(log.getOperatorName())));
        verify(refundMapper).findByRefundOrderId("REF-002");
    }

    @Test
    void shouldResubmitChannelForTaskRetryAndKeepTriggerIdentity() {
        when(refundMapper.restartFailedRefund("REF-TASK")).thenReturn(1);
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

        verify(refundMapper).restartFailedRefund("REF-TASK");
        verify(paymentEventDispatchService).publishRefundSubmit(
                org.mockito.ArgumentMatchers.startsWith("EVTR"),
                org.mockito.ArgumentMatchers.startsWith("RFA"),
                org.mockito.ArgumentMatchers.eq("refund-retry-scheduler"));
        verify(refundMapper).insertRefundAttempt(
                org.mockito.ArgumentMatchers.startsWith("RFA"),
                org.mockito.ArgumentMatchers.eq("REF-TASK"),
                org.mockito.ArgumentMatchers.eq("wx_h5"));
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
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .restartFailedRefund(org.mockito.ArgumentMatchers.anyString());
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
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .restartFailedRefund(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verifyNoInteractions(paymentEventDispatchService);
    }

    @Test
    void shouldLoadRefundDetailWithOperationLogs() {
        RefundDetailDTO detail = new RefundDetailDTO();
        detail.setRefundOrderId("REF-001");
        detail.setCustomerName("张女士");
        RefundOperationLogItemDTO logItem = new RefundOperationLogItemDTO();
        logItem.setLogNo("ROL-001");
        RefundAttemptItemDTO attemptItem = new RefundAttemptItemDTO();
        attemptItem.setRefundAttemptNo("RFA-001");
        when(refundMapper.findDetailByRefundOrderId("REF-001")).thenReturn(detail);
        when(refundMapper.findOperationLogs("REF-001")).thenReturn(Arrays.asList(logItem));
        when(refundMapper.findAttempts("REF-001")).thenReturn(Arrays.asList(attemptItem));

        RefundDetailDTO result = createService().detail("REF-001");

        org.junit.jupiter.api.Assertions.assertEquals("REF-001", result.getRefundOrderId());
        org.junit.jupiter.api.Assertions.assertEquals("张*", result.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getOperationLogs().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, result.getAttempts().size());
        org.junit.jupiter.api.Assertions.assertEquals("RFA-001", result.getAttempts().get(0).getRefundAttemptNo());
        org.junit.jupiter.api.Assertions.assertEquals("张女士", detail.getCustomerName());
        org.junit.jupiter.api.Assertions.assertEquals(null, detail.getOperationLogs());
        verify(refundMapper).findDetailByRefundOrderId("REF-001");
        verify(refundMapper).findOperationLogs("REF-001");
        verify(refundMapper).findAttempts("REF-001");
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
        when(refundMapper.finalizeRefundAttempt("RFA-001", "SUCCESS", "success")).thenReturn(1);
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
    void shouldRejectRefundCallbackControlCharactersBeforeTrimming() {
        RefundServiceImpl service = new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService);
        RefundCallbackRequestDTO invalidRefundOrderId = buildCallbackRequest("SUCCESS");
        invalidRefundOrderId.setRefundOrderId("REF-001\n");
        RefundCallbackRequestDTO invalidRefundAttemptNo = buildCallbackRequest("SUCCESS");
        invalidRefundAttemptNo.setRefundAttemptNo("RFA-001\n");
        RefundCallbackRequestDTO invalidChannelRefundNo = buildCallbackRequest("SUCCESS");
        invalidChannelRefundNo.setChannelRefundNo("CHANNEL-REF-001\n");
        RefundCallbackRequestDTO invalidStatus = buildCallbackRequest("SUCCESS\n");

        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5\n", buildCallbackRequest("SUCCESS")));
        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", invalidRefundOrderId));
        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", invalidRefundAttemptNo));
        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", invalidChannelRefundNo));
        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", invalidStatus));

        org.mockito.Mockito.verifyNoInteractions(paymentCallbackSignatureService);
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
    }

    @Test
    void shouldRequireBoundedRefundAttemptNoBeforeVerifyingCallback() {
        RefundServiceImpl service = new RefundServiceImpl(
                refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService);
        RefundCallbackRequestDTO missingAttemptNo = buildCallbackRequest("SUCCESS");
        missingAttemptNo.setRefundAttemptNo(" ");
        RefundCallbackRequestDTO oversizedAttemptNo = buildCallbackRequest("SUCCESS");
        oversizedAttemptNo.setRefundAttemptNo("R".repeat(65));

        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", missingAttemptNo));
        assertRefundCallbackRequestInvalid(
                () -> service.callback("wx_h5", oversizedAttemptNo));

        org.mockito.Mockito.verifyNoInteractions(paymentCallbackSignatureService);
        org.mockito.Mockito.verifyNoInteractions(refundMapper);
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
    void shouldRejectRepeatedSuccessWhenTerminalFactsAreIncomplete() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        RefundCallbackSourceDTO source = buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001");
        source.setAttemptStatus("ACCEPTED");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> createService().callback("wx_h5", request));

        assertEquals("refund terminal facts are inconsistent", exception.getMessage());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).findByRefundOrderId("REF-001");
    }

    @Test
    void shouldRejectTerminalRefundQueryWhenSuccessTimeIsMissing() {
        RefundCallbackSourceDTO source = buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001");
        source.setSuccessAt(null);
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> createService().query(queryRequest()));

        assertEquals("refund terminal facts are inconsistent", exception.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
    }

    @Test
    void shouldRejectTerminalRefundQueryWhenTerminalTimeIsMissing() {
        RefundCallbackSourceDTO source = buildCallbackSource("FAIL", "wx_h5", "CHANNEL-REF-001");
        source.setTerminalAt(null);
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> createService().query(queryRequest()));

        assertEquals("refund terminal facts are inconsistent", exception.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
    }

    @Test
    void shouldRejectTerminalRefundQueryWhenAttemptCompletionTimeIsMissing() {
        RefundCallbackSourceDTO source = buildCallbackSource("SUCCESS", "wx_h5", "CHANNEL-REF-001");
        source.setAttemptCompletedAt(null);
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(source);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> createService().query(queryRequest()));

        assertEquals("refund terminal facts are inconsistent", exception.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refundChannelQueryService);
    }

    @Test
    void shouldRejectRepeatedFailureWhenSuccessTimeRemains() {
        RefundCallbackRequestDTO request = buildCallbackRequest("FAIL");
        RefundCallbackSourceDTO source = buildCallbackSource("FAIL", "wx_h5", "CHANNEL-REF-001");
        source.setSuccessAt("2026-09-12 10:00:00");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> createService().callback("wx_h5", request));

        assertEquals("refund terminal facts are inconsistent", exception.getMessage());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).findByRefundOrderId("REF-001");
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
    void shouldRejectCallbackBelongingToPreviousRefundAttempt() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        RefundCallbackSourceDTO source = buildCallbackSource(
                "PROCESSING", "wx_h5", "CHANNEL-REF-CURRENT");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.findAttemptNoByChannelRefundNo("wx_h5", "CHANNEL-REF-001"))
                .thenReturn("RFA-PREVIOUS");

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> createService().callback("wx_h5", request));

        assertEquals(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).finalizeRefundAttempt(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectFirstCallbackFromPreviousUnknownRefundAttempt() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        request.setRefundAttemptNo("RFA-PREVIOUS");
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        source.setSubmitEventStatus("UNKNOWN");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> createService().callback("wx_h5", request));

        assertEquals(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .findAttemptNoByChannelRefundNo(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .bindUnknownRefundChannelAccepted(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void shouldRecoverUnknownRefundAcceptanceFromVerifiedCallback() {
        RefundCallbackRequestDTO request = buildCallbackRequest("SUCCESS");
        RefundCallbackSourceDTO source = buildCallbackSource("PROCESSING", "wx_h5", null);
        source.setSubmitEventStatus("UNKNOWN");
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(source);
        when(refundMapper.bindUnknownRefundChannelAccepted(
                "REF-001", "RFA-001", "wx_h5", "CHANNEL-REF-001")).thenReturn(1);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "CHANNEL-REF-001")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("RFA-001")).thenReturn(1);
        when(refundMapper.updateRefundStatus("REF-001", "PROCESSING", "SUCCESS", "success", true))
                .thenReturn(1);
        when(refundMapper.finalizeRefundAttempt("RFA-001", "SUCCESS", "success")).thenReturn(1);

        new RefundServiceImpl(refundMapper, paymentEventDispatchService, paymentCallbackSignatureService,
                refundChannelQueryService).callback("wx_h5", request);

        verify(paymentCallbackSignatureService).verifyRefund("wx_h5", request);
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_ACCEPTANCE_RECOVERED".equals(log.getActionCode())
                        && "channel-callback".equals(log.getOperatorName())));
        verify(refundMapper).markUnknownRefundSubmitEventConfirmed("RFA-001");
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
                        && "RFA-001".equals(log.getRefundAttemptNo())
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
        when(refundMapper.finalizeRefundAttempt("RFA-001", "SUCCESS", "success")).thenReturn(1);
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
                "REF-001", "RFA-001", "wx_h5", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("RFA-001")).thenReturn(1);
        when(refundMapper.findByRefundOrderId("REF-001")).thenReturn(mapperItem);

        RefundListItemDTO result = createService().query(queryRequest());

        assertEquals("PROCESSING", result.getStatus());
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_ACCEPTANCE_RECOVERED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && !log.getOperationRemark().contains("CHANNEL-REF-RECOVERED")));
        verify(refundMapper).markUnknownRefundSubmitEventConfirmed("RFA-001");
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
                "REF-001", "RFA-001", "wx_h5", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.bindRefundAttemptAccepted("RFA-001", "CHANNEL-REF-RECOVERED")).thenReturn(1);
        when(refundMapper.markUnknownRefundSubmitEventConfirmed("RFA-001")).thenReturn(1);

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
        when(refundMapper.finalizeRefundAttempt("RFA-001", "FAIL", "danger")).thenReturn(1);

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
                        && "RFA-001".equals(log.getRefundAttemptNo())
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
        String sensitiveDetail = "jdbc:mysql://internal-db/refund?password=query-secret\n"
                + "select * from refund_order where token='query-token'";
        when(refundChannelQueryService.query(source)).thenThrow(new IllegalStateException(sensitiveDetail));

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, exception.getCode());
        assertEquals("refund channel query failed unexpectedly", exception.getMessage());
        org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(sensitiveDetail));
        org.junit.jupiter.api.Assertions.assertNull(exception.getCause());
        assertEquals(0, exception.getStackTrace().length);
        exception.addSuppressed(new IllegalStateException(sensitiveDetail));
        assertEquals(0, exception.getSuppressed().length);
        verify(refundMapper).insertOperationLog(org.mockito.ArgumentMatchers.argThat(log ->
                "CHANNEL_QUERY_FAILED".equals(log.getActionCode())
                        && "refund-query-operator".equals(log.getOperatorName())
                        && !log.getOperationRemark().contains(sensitiveDetail)));
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
    void shouldRejectChannelQueryResultWhenCurrentRefundAttemptChanged() {
        RefundCallbackSourceDTO snapshot = buildCallbackSource("PROCESSING", "wx_h5", null);
        snapshot.setSubmitEventStatus("UNKNOWN");
        RefundCallbackSourceDTO current = buildCallbackSource("PROCESSING", "wx_h5", null);
        current.setRefundAttemptNo("RFA-002");
        current.setSubmitEventStatus("UNKNOWN");
        when(refundMapper.findCallbackSourceByRefundOrderId("REF-001")).thenReturn(snapshot);
        when(refundChannelQueryService.query(snapshot))
                .thenReturn(buildQueryResult("PROCESSING", "CHANNEL-REF-OLD-ATTEMPT"));
        when(refundMapper.findCallbackSourceForUpdate("REF-001")).thenReturn(current);

        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().query(queryRequest()));

        assertEquals(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH, exception.getCode());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).bindRefundAttemptAccepted(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never()).updateRefundStatus(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.verify(refundMapper, org.mockito.Mockito.never())
                .insertOperationLog(org.mockito.ArgumentMatchers.any());
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
        request.setRefundAttemptNo(" RFA-001 ");
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
        source.setRefundAttemptNo("RFA-001");
        source.setStatus(status);
        source.setChannelCode(channel);
        source.setChannelRefundNo(channelRefundNo);
        boolean terminal = "SUCCESS".equals(status) || "FAIL".equals(status);
        source.setAttemptStatus(terminal ? status : channelRefundNo == null ? "UNKNOWN" : "ACCEPTED");
        source.setAttemptCompletedAt(terminal ? "2026-09-12 10:00:00" : null);
        source.setSuccessAt("SUCCESS".equals(status) ? "2026-09-12 10:00:00" : null);
        source.setTerminalAt(terminal ? "2026-09-12 10:00:00" : null);
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

    private RefundApplyRequestDTO refundApplyRequest(BigDecimal refundAmount) {
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId("PAY-001");
        request.setIdempotencyKey("REFUND-APPLY-001");
        request.setRefundAmount(refundAmount);
        request.setRefundMethod("原路退款");
        request.setOperator("refund-applicant");
        return request;
    }

    private void assertInvalidApplicationIdempotencyKey(
            RefundApplyRequestDTO request,
            String expectedMessage) {
        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, () -> createService().apply(request));
        assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_KEY_INVALID, exception.getCode());
        assertEquals(expectedMessage, exception.getMessage());
    }

    private RefundQueryDTO refundQuery(String refundOrderId,
                                       String paymentOrderId,
                                       String refundStatus,
                                       String refundMethod) {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId(refundOrderId);
        query.setPaymentOrderId(paymentOrderId);
        query.setRefundStatus(refundStatus);
        query.setRefundMethod(refundMethod);
        return query;
    }

    private IllegalArgumentException queryFailure(RefundServiceImpl service, RefundQueryDTO query) {
        return org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.list(query));
    }

    private void assertRefundCallbackRequestInvalid(org.junit.jupiter.api.function.Executable callback) {
        BusinessException exception = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class, callback);
        assertEquals(ErrorCode.REFUND_CALLBACK_REQUEST_INVALID, exception.getCode());
    }

    private IllegalArgumentException applyFailure(RefundApplyRequestDTO request) {
        return org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> createService().apply(request));
    }
}
