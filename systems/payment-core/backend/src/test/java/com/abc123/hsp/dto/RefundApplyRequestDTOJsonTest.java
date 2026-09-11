package com.abc123.hsp.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RefundApplyRequestDTOJsonTest {

    @Test
    void shouldDeserializeExactDecimalRefundAmountFromString() throws Exception {
        RefundApplyRequestDTO request = new ObjectMapper().readValue(
                "{\"refundAmount\":\"9999999999999999.99\"}", RefundApplyRequestDTO.class);

        Assertions.assertEquals(
                new BigDecimal("9999999999999999.99"), request.getRefundAmount());
    }
}
