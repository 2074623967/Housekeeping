package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

@SpringBootTest(properties = {
        "refund-center.outbox-dispatch-mode=LOCAL",
        "spring.datasource.url=jdbc:h2:mem:refund_center_casefold;MODE=MySQL;IGNORECASE=TRUE;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RefundIdempotencyCasefoldIntegrationTest {

    @Autowired
    private RefundService refundService;

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
