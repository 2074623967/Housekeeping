package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.refund.common.BusinessException;
import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
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
}
