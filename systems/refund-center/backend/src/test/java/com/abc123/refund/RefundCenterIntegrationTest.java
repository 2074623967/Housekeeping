package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundDetailDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxDispatchRequestDTO;
import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.mapper.RefundMapper;
import com.abc123.refund.service.RefundService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 使用 H2 验证 DDL、MyBatis XML 和退款状态机的真实联动。
 */
@SpringBootTest(properties = "refund-center.outbox-dispatch-mode=LOCAL")
class RefundCenterIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldSerializeConcurrentRefundApplicationsWithinOriginalPaymentAmount() throws Exception {
        String paymentOrderId = "PAY-IT-CONCURRENT-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-CONCURRENT");
        source.setPaidAmount(new BigDecimal("100.00"));
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO first = new RefundApplyRequestDTO();
        first.setPaymentOrderId(paymentOrderId);
        first.setRefundAmount(new BigDecimal("70.00"));
        first.setIdempotencyKey("FIRST-" + paymentOrderId);
        RefundApplyRequestDTO second = new RefundApplyRequestDTO();
        second.setPaymentOrderId(paymentOrderId);
        second.setRefundAmount(new BigDecimal("60.00"));
        second.setIdempotencyKey("SECOND-" + paymentOrderId);
        RefundApplyRequestDTO replay = new RefundApplyRequestDTO();
        replay.setPaymentOrderId(paymentOrderId);
        replay.setRefundAmount(new BigDecimal("70.00"));
        replay.setIdempotencyKey(first.getIdempotencyKey());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Future<RefundListItemDTO>> waiting = new AtomicReference<>();
        AtomicReference<Future<RefundListItemDTO>> repeated = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(2);
        try {
            RefundListItemDTO created = new TransactionTemplate(transactionManager).execute(status -> {
                assertNotNull(refundMapper.findPaymentSourceForUpdate(paymentOrderId));
                waiting.set(executor.submit(() -> {
                    started.countDown();
                    return refundService.apply(second);
                }));
                repeated.set(executor.submit(() -> {
                    started.countDown();
                    return refundService.apply(replay);
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
                assertThrows(TimeoutException.class, () -> waiting.get().get(200, TimeUnit.MILLISECONDS));
                assertThrows(TimeoutException.class, () -> repeated.get().get(200, TimeUnit.MILLISECONDS));
                return refundService.apply(first);
            });

            assertNotNull(created);
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> waiting.get().get(10, TimeUnit.SECONDS));
            assertTrue(rejected.getCause() instanceof BusinessException);
            assertEquals(created.getRefundOrderId(), repeated.get().get(10, TimeUnit.SECONDS).getRefundOrderId());
            RefundQueryDTO query = new RefundQueryDTO();
            query.setPaymentOrderId(paymentOrderId);
            assertEquals(1L, refundService.list(query).getTotal());
            assertEquals(new BigDecimal("70.00"), refundService.list(query).getItems().get(0).getRefundAmount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCompleteRefundFlowWithDatabase() {
        String paymentOrderId = "PAY-IT-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-1");
        source.setCustomerName("集成测试客户");
        source.setPaidAmount(new BigDecimal("120.00"));
        source.setChannelCode("WECHAT");
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("30.00"));
        apply.setRefundReason("服务取消");
        RefundListItemDTO created = refundService.apply(apply);
        assertTrue(created.getRefundOrderId().matches("REF\\d{17}[0-9a-f]{32}"));
        assertNotNull(created.getAppliedAt());
        assertEquals("WECHAT", created.getChannelCode());
        assertNull(created.getChannelSubmittedAt());

        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(created.getRefundOrderId());
        assertEquals("APPROVED", refundService.approve(action).getStatus());
        RefundListItemDTO submitted = refundService.submit(action);
        assertEquals("PROCESSING", submitted.getStatus());
        assertEquals("WECHAT", submitted.getChannelCode());
        assertNotNull(submitted.getChannelSubmittedAt());

        RefundCallbackRequestDTO callback = new RefundCallbackRequestDTO();
        callback.setRefundOrderId(created.getRefundOrderId());
        callback.setResult("SUCCESS");
        callback.setChannelRefundId("CH-IT-1");
        RefundListItemDTO completed = refundService.callback(callback);
        assertEquals("SUCCESS", completed.getStatus());
        assertEquals("WECHAT", completed.getChannelCode());
        assertNotNull(completed.getChannelSubmittedAt());
        assertNotNull(completed.getSuccessAt());
        assertNotNull(completed.getTerminalAt());
        assertEquals(completed.getSuccessAt(), completed.getTerminalAt());
        RefundDetailDTO completedDetail = refundService.detail(created.getRefundOrderId());
        assertEquals("WECHAT", completedDetail.getChannelCode());
        assertEquals(completed.getChannelSubmittedAt(), completedDetail.getChannelSubmittedAt());
        assertEquals(completed.getTerminalAt(), completedDetail.getTerminalAt());
        assertEquals(4, completedDetail.getOperationLogs().size());
        RefundListItemDTO repeated = refundService.callback(callback);
        assertEquals("SUCCESS", repeated.getStatus());
        assertEquals(completed.getTerminalAt(), repeated.getTerminalAt());
        assertEquals(4, refundService.detail(created.getRefundOrderId()).getOperationLogs().size());

        callback.setChannelRefundId("CH-IT-CONFLICT");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.abc123.refund.common.BusinessException.class,
                () -> refundService.callback(callback));
        assertEquals("CH-IT-1", refundService.detail(created.getRefundOrderId()).getChannelRefundId());

        RefundOutboxQueryDTO outboxQuery = new RefundOutboxQueryDTO();
        outboxQuery.setAggregateId(created.getRefundOrderId());
        RefundOutboxItemDTO outbox = refundService.listOutbox(outboxQuery).getItems().get(0);
        assertEquals("PENDING", outbox.getStatus());

        RefundOutboxDispatchRequestDTO dispatch = new RefundOutboxDispatchRequestDTO();
        dispatch.setSimulateResult("SUCCESS");
        assertEquals("SENT", refundService.dispatchOutbox(outbox.getEventId(), dispatch).getStatus());
    }

    @Test
    void shouldRecordTerminalTimeForFailedCallback() {
        String paymentOrderId = "PAY-IT-FAIL-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-FAIL");
        source.setCustomerName("失败退款客户");
        source.setPaidAmount(new BigDecimal("90.00"));
        source.setChannelCode("WECHAT");
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("12.00"));
        RefundListItemDTO created = refundService.apply(apply);
        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(created.getRefundOrderId());
        refundService.approve(action);
        refundService.submit(action);

        RefundCallbackRequestDTO callback = new RefundCallbackRequestDTO();
        callback.setRefundOrderId(created.getRefundOrderId());
        callback.setResult("FAIL");
        callback.setFailureCode("CHANNEL_TIMEOUT");
        RefundListItemDTO failed = refundService.callback(callback);

        assertEquals("FAIL", failed.getStatus());
        assertNull(failed.getSuccessAt());
        assertEquals("CHANNEL_TIMEOUT", failed.getFailureCode());
        assertNotNull(failed.getTerminalAt());
        assertEquals(failed.getTerminalAt(), refundService.detail(created.getRefundOrderId()).getTerminalAt());
        assertEquals(failed.getTerminalAt(), refundService.callback(callback).getTerminalAt());

        callback.setFailureCode("CHANNEL_CONFLICT");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.abc123.refund.common.BusinessException.class,
                () -> refundService.callback(callback));
        assertEquals("CHANNEL_TIMEOUT", refundService.detail(created.getRefundOrderId()).getFailureCode());
    }

    @Test
    void shouldRejectConflictingRefundIdempotencyReplay() {
        String paymentOrderId = "PAY-IT-IDEMPOTENCY-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-IDEMPOTENCY");
        source.setCustomerName("幂等客户");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setChannelCode("WECHAT");
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO first = new RefundApplyRequestDTO();
        first.setPaymentOrderId(paymentOrderId);
        first.setRefundAmount(new BigDecimal("20.00"));
        first.setRefundReason("服务取消");
        first.setIdempotencyKey("REF-IDEMPOTENCY-1");
        RefundListItemDTO created = refundService.apply(first);

        RefundApplyRequestDTO conflict = new RefundApplyRequestDTO();
        conflict.setPaymentOrderId(paymentOrderId);
        conflict.setRefundAmount(new BigDecimal("21.00"));
        conflict.setRefundReason("服务取消");
        conflict.setIdempotencyKey("REF-IDEMPOTENCY-1");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.abc123.refund.common.BusinessException.class,
                () -> refundService.apply(conflict));

        assertEquals(created.getRefundOrderId(), refundService.apply(first).getRefundOrderId());
    }

    @Test
    void shouldAcceptMaxLengthIdempotencyKeyAndReplaySameRefund() {
        String paymentOrderId = "PAY-IT-KEY-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-KEY");
        source.setPaidAmount(new BigDecimal("100.00"));
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(paymentOrderId);
        request.setRefundAmount(new BigDecimal("10.00"));
        request.setIdempotencyKey(String.join("", java.util.Collections.nCopies(128, "x")));
        RefundListItemDTO created = refundService.apply(request);
        assertEquals(created.getRefundOrderId(), refundService.apply(request).getRefundOrderId());

        request.setIdempotencyKey(request.getIdempotencyKey() + "x");
        assertThrows(BusinessException.class, () -> refundService.apply(request));
        RefundQueryDTO query = new RefundQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        assertEquals(1L, refundService.list(query).getTotal());
    }

    @Test
    void shouldRejectConflictingPaymentSuccessProjection() {
        String paymentOrderId = "PAY-IT-REPLAY-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-REPLAY");
        source.setCustomerName("重复投影客户");
        source.setPaidAmount(new BigDecimal("88.00"));
        source.setChannelCode("WECHAT");

        refundService.projectPaymentSuccess(source);
        source.setCustomerName("重复投影客户-更新");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.abc123.refund.common.BusinessException.class,
                () -> refundService.projectPaymentSuccess(source));

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        source.setCustomerName("重复投影客户");
        refundService.projectPaymentSuccess(source);
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("18.00"));
        RefundListItemDTO refund = refundService.apply(apply);

        assertEquals("重复投影客户", refund.getCustomerName());
        assertEquals("ORDER-IT-REPLAY", refund.getOrderNo());
    }

    @Test
    void shouldExportRefundDetailSnapshot() {
        String paymentOrderId = "PAY-IT-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-IT-2");
        source.setCustomerName("导出测试客户");
        source.setPaidAmount(new BigDecimal("200.00"));
        source.setChannelCode("ALIPAY");
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("66.00"));
        apply.setRefundReason("导出快照测试");
        RefundListItemDTO created = refundService.apply(apply);

        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(created.getRefundOrderId());
        refundService.approve(action);
        refundService.submit(action);

        String csv = new String(refundService.exportDetailSnapshot(created.getRefundOrderId()), StandardCharsets.UTF_8);
        assertEquals(true, csv.contains("退款单号,原支付单号,业务订单号"));
        assertEquals(true, csv.contains("状态,状态样式,渠道退款号"));
        assertEquals(true, csv.contains(created.getRefundOrderId()));
        assertEquals(true, csv.contains("操作日志"));
        assertEquals(true, csv.contains("退款成功事件出站"));
    }
}
