package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.refund.dto.RefundOutboxItemDTO;
import com.abc123.refund.dto.RefundOutboxQueryDTO;
import com.abc123.refund.mapper.RefundMapper;
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
        "spring.datasource.url=jdbc:h2:mem:refund_center_outbox_literal;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class RefundOutboxLiteralSearchIntegrationTest {

    @Autowired
    private RefundMapper refundMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    void shouldTreatEventAndAggregateNumbersAsLiteralSubstrings() {
        String eventId = "EVT-%_!\\\"-" + UUID.randomUUID();
        String aggregateId = "REF-%_!\\\"-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO t_refund_outbox_event "
                        + "(event_id, event_type, aggregate_id, payload_json, status) VALUES (?, ?, ?, ?, ?)",
                eventId, "REFUND_SUCCESS", aggregateId, "{}", "FAIL");
        jdbcTemplate.update("INSERT INTO t_refund_outbox_event "
                        + "(event_id, event_type, aggregate_id, payload_json, status) VALUES (?, ?, ?, ?, ?)",
                "EVT-ORDINARY-" + UUID.randomUUID(), "REFUND_SUCCESS",
                "REF-ORDINARY-" + UUID.randomUUID(), "{}", "FAIL");

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", eventId)) {
            RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
            query.setEventId(fragment);
            query.setStatus("FAIL");
            assertEquals(Arrays.asList(eventId), refundMapper.findOutboxList(query).stream()
                    .map(RefundOutboxItemDTO::getEventId).collect(Collectors.toList()), fragment);
            assertEquals(1L, refundMapper.countOutbox(query), fragment);
            assertEquals(fragment, query.getEventId());
        }

        for (String fragment : Arrays.asList("%", "_", "!", "\\", "\"", aggregateId)) {
            RefundOutboxQueryDTO query = new RefundOutboxQueryDTO();
            query.setAggregateId(fragment);
            query.setStatus("FAIL");
            assertEquals(Arrays.asList(eventId), refundMapper.findOutboxList(query).stream()
                    .map(RefundOutboxItemDTO::getEventId).collect(Collectors.toList()), fragment);
            assertEquals(1L, refundMapper.countOutbox(query), fragment);
            assertEquals(fragment, query.getAggregateId());
        }
    }
}
