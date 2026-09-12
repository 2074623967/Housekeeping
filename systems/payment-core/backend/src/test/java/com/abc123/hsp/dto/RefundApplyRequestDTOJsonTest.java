package com.abc123.hsp.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RefundApplyRequestDTOJsonTest {

    @Test
    void shouldDeserializeExactDecimalRefundAmountFromString() throws Exception {
        RefundApplyRequestDTO request = new ObjectMapper().readValue(
                "{\"idempotencyKey\":\"REFUND-APPLY-001\","
                        + "\"refundAmount\":\"9999999999999999.99\"}", RefundApplyRequestDTO.class);

        Assertions.assertEquals("REFUND-APPLY-001", request.getIdempotencyKey());
        Assertions.assertEquals(
                new BigDecimal("9999999999999999.99"), request.getRefundAmount());
    }
}
