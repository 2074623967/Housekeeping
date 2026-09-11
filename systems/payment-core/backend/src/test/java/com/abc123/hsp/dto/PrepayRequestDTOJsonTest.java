package com.abc123.hsp.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PrepayRequestDTOJsonTest {

    @Test
    void shouldDeserializeExactDecimalAmountFromString() throws Exception {
        PrepayRequestDTO request = new ObjectMapper().readValue(
                "{\"amount\":\"9999999999999999.99\"}", PrepayRequestDTO.class);

        Assertions.assertEquals(
                new BigDecimal("9999999999999999.99"), request.getAmount());
    }
}
