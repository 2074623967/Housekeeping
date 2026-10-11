package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.refund.dto.PaymentSuccessProjectionDTO;
import com.abc123.refund.dto.RefundActionRequestDTO;
import com.abc123.refund.dto.RefundApplyRequestDTO;
import com.abc123.refund.dto.RefundCallbackRequestDTO;
import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.service.RefundService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "refund-center.outbox-dispatch-mode=LOCAL",
        "spring.datasource.url=jdbc:h2:mem:refund_outbox_rollback;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RefundSuccessOutboxRollbackIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldRollBackSuccessfulCallbackWhenOutboxInsertFailsAndAllowRetry() {
        String paymentOrderId = "PAY-OUTBOX-" + UUID.randomUUID();
        PaymentSuccessProjectionDTO source = new PaymentSuccessProjectionDTO();
        source.setPaymentOrderId(paymentOrderId);
        source.setOrderNo("ORDER-OUTBOX");
        source.setPaidAmount(new BigDecimal("20.00"));
        refundService.projectPaymentSuccess(source);

        RefundApplyRequestDTO apply = new RefundApplyRequestDTO();
        apply.setPaymentOrderId(paymentOrderId);
        apply.setRefundAmount(new BigDecimal("10.00"));
        RefundListItemDTO refund = refundService.apply(apply);
        RefundActionRequestDTO action = new RefundActionRequestDTO();
        action.setRefundOrderId(refund.getRefundOrderId());
        refundService.approve(action);
        refundService.submit(action);

        RefundCallbackRequestDTO callback = new RefundCallbackRequestDTO();
        callback.setRefundOrderId(refund.getRefundOrderId());
        callback.setResult("SUCCESS");
        callback.setChannelRefundId("CH-OUTBOX");
        RefundOutboxQueryDTO outboxQuery = new RefundOutboxQueryDTO();
        outboxQuery.setAggregateId(refund.getRefundOrderId());

        jdbcTemplate.execute("ALTER TABLE t_refund_outbox_event ADD CONSTRAINT reject_outbox_test "
                + "CHECK (aggregate_id <> '" + refund.getRefundOrderId() + "')");
        try {
            assertThrows(RuntimeException.class, () -> refundService.callback(callback));
            assertEquals("PROCESSING", refundService.detail(refund.getRefundOrderId()).getStatus());
            assertEquals(3, refundService.detail(refund.getRefundOrderId()).getOperationLogs().size());
            assertEquals(0L, refundService.listOutbox(outboxQuery).getTotal());
        } finally {
            jdbcTemplate.execute("ALTER TABLE t_refund_outbox_event DROP CONSTRAINT reject_outbox_test");
        }

        assertEquals("SUCCESS", refundService.callback(callback).getStatus());
        assertEquals(1L, refundService.listOutbox(outboxQuery).getTotal());
    }
}
