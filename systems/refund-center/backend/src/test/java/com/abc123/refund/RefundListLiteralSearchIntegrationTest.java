package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.refund.dto.RefundListItemDTO;
import com.abc123.refund.dto.RefundQueryDTO;
import com.abc123.refund.mapper.RefundMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "refund-center.outbox-dispatch-mode=LOCAL",
        "spring.datasource.url=jdbc:h2:mem:refund_center_literal_search;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RefundListLiteralSearchIntegrationTest {

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldTreatRefundAndPaymentNumbersAsLiteralSubstrings() {
        String refundOrderId = "REF-%_!\\\"-" + UUID.randomUUID();
        String paymentOrderId = "PAY-%_!\\\"-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_refund_order "
                        + "(refund_order_id, payment_order_id, order_no, paid_amount, refund_amount, "
                        + "refund_method, refund_reason, status, idempotency_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                refundOrderId, paymentOrderId, "ORDER-LITERAL", new BigDecimal("100.00"),
                new BigDecimal("10.00"), "ORIGINAL", "isolated search fixture", "REVIEWING",
                "KEY-LITERAL-" + UUID.randomUUID());

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", refundOrderId)) {
            RefundQueryDTO query = new RefundQueryDTO();
            query.setRefundOrderId(fragment);
            assertEquals(Arrays.asList(refundOrderId), refundMapper.findList(query).stream()
                    .map(RefundListItemDTO::getRefundOrderId).collect(Collectors.toList()), fragment);
            assertEquals(1L, refundMapper.count(query), fragment);
            assertEquals(fragment, query.getRefundOrderId());
        }

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", paymentOrderId)) {
            RefundQueryDTO query = new RefundQueryDTO();
            query.setPaymentOrderId(fragment);
            assertEquals(Arrays.asList(refundOrderId), refundMapper.findList(query).stream()
                    .map(RefundListItemDTO::getRefundOrderId).collect(Collectors.toList()), fragment);
            assertEquals(1L, refundMapper.count(query), fragment);
            assertEquals(fragment, query.getPaymentOrderId());
        }
    }
}
