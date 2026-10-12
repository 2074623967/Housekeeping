package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.service.RefundService;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "refund-center.outbox-dispatch-mode=LOCAL",
        "spring.datasource.url=jdbc:h2:mem:refund_center_casefold;MODE=MySQL;IGNORECASE=TRUE;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RefundIdempotencyCasefoldIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNotApplyRefundAgainstAliasedPaymentSource() {
        String paymentOrderId = "PAY-SOURCE-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-SOURCE");
        source.setPaidAmount(new BigDecimal("100.00"));
        refundService.projectPaymentSuccess(source);

        source.setPaymentOrderId(paymentOrderId.toLowerCase(Locale.ROOT));
        assertThrows(BusinessException.class, () -> refundService.projectPaymentSuccess(source));

        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(paymentOrderId.toLowerCase(Locale.ROOT));
        request.setRefundAmount(new BigDecimal("20.00"));
        assertThrows(BusinessException.class, () -> refundService.apply(request));

        RefundQueryDTO query = new RefundQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        assertEquals(0L, refundService.list(query).getTotal());
    }

    @Test
    void shouldNotCountAliasedHistoricalRefundAgainstCanonicalPaymentCapacity() {
        String paymentOrderId = "PAY-CAPACITY-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-CAPACITY");
        source.setPaidAmount(new BigDecimal("100.00"));
        source.setPayScene("HOME_CLEAN");
        refundService.projectPaymentSuccess(source);

        String aliasedRefundOrderId = "REF-ALIAS-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_refund_order "
                        + "(refund_order_id, payment_order_id, order_no, paid_amount, refund_amount, "
                        + "refund_method, refund_reason, status, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                aliasedRefundOrderId, paymentOrderId.toLowerCase(Locale.ROOT), "ORDER-CAPACITY",
                new BigDecimal("100.00"), new BigDecimal("90.00"), "ORIGINAL", "historical alias",
                "PROCESSING", "KEY-ALIAS-" + UUID.randomUUID());

        assertNull(refundService.detail(aliasedRefundOrderId).getPayScene());
        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(paymentOrderId);
        request.setRefundAmount(new BigDecimal("20.00"));
        assertEquals("REVIEWING", refundService.apply(request).getStatus());
    }

    @Test
    void shouldKeepDifferentCaseIdempotencyKeysIndependent() {
        String paymentOrderId = "PAY-CASE-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-CASE");
        source.setPaidAmount(new BigDecimal("100.00"));
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO request = new RefundApplyRequestDTO();
        request.setPaymentOrderId(paymentOrderId);
        request.setRefundAmount(new BigDecimal("20.00"));
        request.setIdempotencyKey("refund-" + UUID.randomUUID());
        String firstKey = request.getIdempotencyKey();
        RefundListItemDTO first = refundService.apply(request);

        request.setIdempotencyKey(request.getIdempotencyKey().toUpperCase(Locale.ROOT));
        RefundListItemDTO second = refundService.apply(request);
        assertNotEquals(first.getRefundOrderId(), second.getRefundOrderId());
        assertEquals(second.getRefundOrderId(), refundService.apply(request).getRefundOrderId());
        request.setIdempotencyKey(firstKey);
        assertEquals(first.getRefundOrderId(), refundService.apply(request).getRefundOrderId());

        RefundQueryDTO query = new RefundQueryDTO();
        query.setPaymentOrderId(paymentOrderId);
        assertEquals(2L, refundService.list(query).getTotal());
    }

    @Test
    void shouldRejectAliasedRefundOrderIdAcrossActionsAndCallbacks() {
        String paymentOrderId = "PAY-ACTION-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-ACTION");
        source.setPaidAmount(new BigDecimal("100.00"));
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("20.00"));
        String refundOrderId = refundService.apply(apply).getRefundOrderId();
        String alias = refundOrderId.toLowerCase(Locale.ROOT);

        assertThrows(BusinessException.class, () -> refundService.detail(alias));
        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(alias);
        assertThrows(BusinessException.class, () -> refundService.approve(action));
        assertEquals("REVIEWING", refundService.detail(refundOrderId).getStatus());

        action.setRefundOrderId(refundOrderId);
        assertEquals("APPROVED", refundService.approve(action).getStatus());
        action.setRefundOrderId(alias);
        assertThrows(BusinessException.class, () -> refundService.submit(action));
        assertEquals("APPROVED", refundService.detail(refundOrderId).getStatus());

        action.setRefundOrderId(refundOrderId);
        assertEquals("PROCESSING", refundService.submit(action).getStatus());
        RefundCallbackRequestDTO callback = new RefundCallbackRequestDTO();
        callback.setRefundOrderId(alias);
        callback.setResult("SUCCESS");
        callback.setChannelRefundId("CH-ACTION");
        assertThrows(BusinessException.class, () -> refundService.callback(callback));
        assertEquals("PROCESSING", refundService.detail(refundOrderId).getStatus());
        assertEquals(3, refundService.detail(refundOrderId).getOperationLogs().size());

        callback.setRefundOrderId(refundOrderId);
        assertEquals("SUCCESS", refundService.callback(callback).getStatus());
        callback.setRefundOrderId(alias);
        assertThrows(BusinessException.class, () -> refundService.callback(callback));
        jdbcTemplate.update("INSERT INTO t_refund_operation_log "
                        + "(log_no, refund_order_id, action_code, action_name, to_status, operator_name) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "RLOG-ALIAS-" + UUID.randomUUID(), alias, "LEGACY", "legacy alias", "SUCCESS", "test");
        assertEquals(4, refundService.detail(refundOrderId).getOperationLogs().size());
        RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
        query.setAggregateId(refundOrderId);
        assertEquals(1L, refundService.listOutbox(query).getTotal());
    }

    @Test
    void shouldRejectAliasedOutboxEventIdWithoutDispatching() {
        String eventId = "REVT-ALIAS-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_refund_outbox_event "
                        + "(event_id, event_type, aggregate_id, payload_json, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                eventId, "REFUND_SUCCESS", "REF-ALIAS-" + UUID.randomUUID(), "{}", "PENDING");

        String alias = eventId.toLowerCase(Locale.ROOT);
        assertThrows(BusinessException.class, () -> refundService.dispatchOutbox(alias, null));
        String overlappingEventId = "PREFIX-" + eventId;
        jdbcTemplate.update("INSERT INTO t_refund_outbox_event "
                        + "(event_id, event_type, aggregate_id, payload_json, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                overlappingEventId, "REFUND_SUCCESS", "REF-OVERLAP-" + UUID.randomUUID(), "{}", "PENDING");
        RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
        query.setEventId(eventId);
        assertEquals(2L, refundService.listOutbox(query).getTotal());
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM t_refund_outbox_event WHERE event_id = ?", String.class, eventId));

        assertEquals("SENT", refundService.dispatchOutbox(eventId, null).getStatus());
        assertThrows(BusinessException.class, () -> refundService.dispatchOutbox(alias, null));
        assertEquals("SENT", refundService.dispatchOutbox(eventId, null).getStatus());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT retry_count FROM t_refund_outbox_event WHERE event_id = ?", Integer.class, eventId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT status FROM t_refund_outbox_event WHERE event_id = ?", String.class, overlappingEventId));
    }
}
